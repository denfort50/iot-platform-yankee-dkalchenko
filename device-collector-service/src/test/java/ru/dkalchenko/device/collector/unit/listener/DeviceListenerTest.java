package ru.dkalchenko.device.collector.unit.listener;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.kafka.support.Acknowledgment;
import ru.dkalchenko.device.collector.listener.DeviceListener;
import ru.dkalchenko.device.collector.mapper.DeviceMapper;
import ru.dkalchenko.device.collector.metrics.CollectorMetrics;
import ru.dkalchenko.device.collector.service.DeviceService;
import ru.dkalchenko.device.collector.tracing.KafkaTraceContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты слушателя Kafka: обработка сообщения из MockConsumer,
 * проверка десериализованного Avro-контента и вызов сервиса.
 */
@ExtendWith(MockitoExtension.class)
class DeviceListenerTest {

    static final String TOPIC = "devices.v1";
    static final int PARTITION = 0;
    static final long OFFSET = 100L;
    static final String KEY = "device-001";

    Schema deviceSchema;
    MockConsumer<String, GenericRecord> mockConsumer;

    @Mock
    DeviceService deviceService;
    @Mock
    DeviceMapper deviceMapper;
    @Mock
    CollectorMetrics metrics;
    @Mock
    KafkaTraceContextRunner kafkaTraceContextRunner;
    @Mock
    Acknowledgment acknowledgment;

    @InjectMocks
    DeviceListener deviceListener;

    @BeforeEach
    void setUp() {
        when(deviceMapper.getOperation(any())).thenReturn("CREATE");
        doAnswer((Answer<Void>) inv -> {
            inv.getArgument(4, Runnable.class).run();
            return null;
        }).when(kafkaTraceContextRunner).runInSpan(any(), any(), any(), any(), any(Runnable.class));
        deviceSchema = new Schema.Parser().parse("""
            {"type":"record","name":"DeviceDto","namespace":"com.nashkod.avro",
             "fields":[
               {"name":"operation","type":"string"},
               {"name":"deviceId","type":"string"},
               {"name":"deviceType","type":"string"},
               {"name":"createdAt","type":"long"},
               {"name":"meta","type":"string"}
             ]}
            """);
        mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        mockConsumer.assign(java.util.Collections.singletonList(new TopicPartition(TOPIC, PARTITION)));
        mockConsumer.updateBeginningOffsets(java.util.Map.of(new TopicPartition(TOPIC, PARTITION), 0L));
    }

    private GenericRecord createAvroRecord(String deviceId, String deviceType, long createdAt, String meta) {
        GenericRecord record = new GenericData.Record(deviceSchema);
        record.put("operation", "CREATE");
        record.put("deviceId", deviceId);
        record.put("deviceType", deviceType);
        record.put("createdAt", createdAt);
        record.put("meta", meta);
        return record;
    }

    @Test
    void whenValidRecordFromConsumerThenCallsServiceAndAcks() {
        GenericRecord avroRecord = createAvroRecord(KEY, "sensor", 1_700_000_000_000L, "{}");
        ConsumerRecord<String, GenericRecord> consumerRecord = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, KEY, avroRecord);
        mockConsumer.addRecord(consumerRecord);

        java.util.List<ConsumerRecord<String, GenericRecord>> records = mockConsumer.poll(java.time.Duration.ZERO).records(new TopicPartition(TOPIC, PARTITION));
        assertThat(records).hasSize(1);
        ConsumerRecord<String, GenericRecord> polled = records.getFirst();

        deviceListener.handleDeviceMessage(polled, acknowledgment);

        ArgumentCaptor<GenericRecord> recordCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(deviceService).processDevice(recordCaptor.capture());
        GenericRecord passed = recordCaptor.getValue();
        assertThat(passed.get("deviceId").toString()).isEqualTo(KEY);
        assertThat(passed.get("deviceType").toString()).isEqualTo("sensor");
        assertThat(passed.get("createdAt")).isEqualTo(1_700_000_000_000L);
        verify(acknowledgment).acknowledge();
        verify(metrics).incrementDevicesProcessed(eq(KEY));
    }

    @Test
    void whenServiceThrowsIllegalArgumentThenRethrowsAndIncrementsFailed() {
        GenericRecord avroRecord = createAvroRecord(KEY, "sensor", 1_700_000_000_000L, "{}");
        ConsumerRecord<String, GenericRecord> consumerRecord = new ConsumerRecord<>(TOPIC, PARTITION, OFFSET, KEY, avroRecord);
        mockConsumer.addRecord(consumerRecord);
        java.util.List<ConsumerRecord<String, GenericRecord>> records = mockConsumer.poll(java.time.Duration.ZERO).records(new TopicPartition(TOPIC, PARTITION));
        ConsumerRecord<String, GenericRecord> polled = records.getFirst();

        org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid device data"))
                .when(deviceService).processDevice(any());

        assertThatThrownBy(() -> deviceListener.handleDeviceMessage(polled, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid device data");

        verify(metrics).incrementDevicesFailed(KEY);
        verify(acknowledgment, never()).acknowledge();
    }
}
