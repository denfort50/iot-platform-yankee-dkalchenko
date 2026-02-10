package ru.dkalchenko.collector.unit.service;

import io.opentelemetry.api.OpenTelemetry;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.collector.client.RedisClient;
import ru.dkalchenko.collector.mapper.DeviceDtoAvroSchema;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.properties.OutboxProperties;
import ru.dkalchenko.collector.repository.OutboxRepository;
import ru.dkalchenko.collector.service.DevicePublisher;
import ru.dkalchenko.collector.service.InstanceRegistry;
import ru.dkalchenko.collector.service.OutboxProcessor;
import ru.dkalchenko.collector.metrics.CollectorMetrics;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    static final String PUBLISHED_KEY = "devices:published";
    static final long TTL = 604800L;
    static final int BATCH_SIZE = 10;
    static final OutboxProperties OUTBOX_PROPERTIES = new OutboxProperties(
            null, null, 0, (short) 0, BATCH_SIZE, PUBLISHED_KEY, TTL);

    @Mock
    OpenTelemetry openTelemetry;
    @Mock
    OutboxRepository outboxRepository;
    @Mock
    InstanceRegistry instanceRegistry;
    @Mock
    RedisClient redisClient;
    @Mock
    DevicePublisher devicePublisher;
    @Mock
    DeviceDtoAvroSchema deviceDtoAvroSchema;
    @Mock
    GenericRecord genericRecord;
    @Mock
    CollectorMetrics collectorMetrics;

    @InjectMocks
    OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        // Подменяем OutboxProperties
        org.springframework.test.util.ReflectionTestUtils.setField(
                outboxProcessor, "outboxProperties", OUTBOX_PROPERTIES);
    }

    @Test
    void whenSelectedNRecordsThenSentNMessagesAndMarkAsSentNCalled() {
        int total = 2;
        int myIndex = 1;
        when(instanceRegistry.getTotalInstances()).thenReturn(total);
        when(instanceRegistry.getMyIndex()).thenReturn(myIndex);

        DeviceOutbox row1 = new DeviceOutbox("device-1", LocalDateTime.now(), 0, null, null, null);
        DeviceOutbox row2 = new DeviceOutbox("device-2", LocalDateTime.now(), 0, null, null, null);
        when(outboxRepository.findPendingForShard(total, myIndex, BATCH_SIZE))
                .thenReturn(List.of(row1, row2))
                .thenReturn(List.of());

        when(redisClient.sAddWithExpire(eq(PUBLISHED_KEY), eq(TTL), eq("device-1"))).thenReturn(1L);
        when(redisClient.sAddWithExpire(eq(PUBLISHED_KEY), eq(TTL), eq("device-2"))).thenReturn(1L);
        when(deviceDtoAvroSchema.createRecord("device-1")).thenReturn(genericRecord);
        when(deviceDtoAvroSchema.createRecord("device-2")).thenReturn(genericRecord);

        outboxProcessor.processOutbox();

        verify(devicePublisher, times(2)).publish(any(GenericRecord.class), isNull());
        verify(outboxRepository).markAsSent("device-1");
        verify(outboxRepository).markAsSent("device-2");
        verify(outboxRepository, never()).incrementAttempts(any(), any());
        verify(collectorMetrics, times(2)).incrementOutboxPublishSuccess();
        verify(collectorMetrics, never()).incrementOutboxPublishFail();
    }

    @Test
    void whenPublishErrorThenIncrementAttemptsAndRecordRemains() {
        int total = 2;
        int myIndex = 1;
        when(instanceRegistry.getTotalInstances()).thenReturn(total);
        when(instanceRegistry.getMyIndex()).thenReturn(myIndex);

        DeviceOutbox row = new DeviceOutbox("device-fail", LocalDateTime.now(), 0, null, null, null);
        when(outboxRepository.findPendingForShard(total, myIndex, BATCH_SIZE))
                .thenReturn(List.of(row))
                .thenReturn(List.of());

        when(redisClient.sAddWithExpire(eq(PUBLISHED_KEY), eq(TTL), eq("device-fail"))).thenReturn(1L);
        when(deviceDtoAvroSchema.createRecord("device-fail")).thenReturn(genericRecord);
        doThrow(new RuntimeException("Kafka unavailable")).when(devicePublisher).publish(eq(genericRecord), any());

        outboxProcessor.processOutbox();

        verify(redisClient).sRem(PUBLISHED_KEY, "device-fail");
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxRepository).incrementAttempts(eq("device-fail"), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("Kafka unavailable");
        verify(outboxRepository, never()).markAsSent(any());
        verify(collectorMetrics).incrementOutboxPublishFail();
        verify(collectorMetrics, never()).incrementOutboxPublishSuccess();
    }

    @Test
    void whenInstanceNotInRegistryThenSkipsProcessing() {
        when(instanceRegistry.getTotalInstances()).thenReturn(2);
        when(instanceRegistry.getMyIndex()).thenReturn(0);

        outboxProcessor.processOutbox();

        verify(outboxRepository, never()).findPendingForShard(anyInt(), anyInt(), anyInt());
    }
}
