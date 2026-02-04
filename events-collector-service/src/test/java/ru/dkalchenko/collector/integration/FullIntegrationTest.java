package ru.dkalchenko.collector.integration;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.dkalchenko.collector.service.InstanceRegistry;
import ru.dkalchenko.collector.service.OutboxProcessor;
import ru.dkalchenko.collector.testcontainer.base.AbstractIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Интеграционный тест полного цикла: events.v1 → device_events + device_outbox → processOutbox → devices.v1.
 */
class FullIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENTS_TOPIC = "events.v1";
    private static final String DEVICES_TOPIC = "devices.v1";
    private static final Duration CONSUMER_POLL_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaTemplate<String, GenericRecord> deviceKafkaTemplate;

    @Autowired
    ConsumerFactory<String, GenericRecord> kafkaAvroConsumerFactory;

    @Autowired
    OutboxProcessor outboxProcessor;

    @Autowired
    InstanceRegistry instanceRegistry;

    private static Schema deviceEventSchema;

    @BeforeEach
    void loadDeviceEventSchema() throws Exception {
        if (deviceEventSchema == null) {
            try (var in = new ClassPathResource("avro/DeviceEvent.avsc").getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                deviceEventSchema = new Schema.Parser().parse(json);
            }
        }
    }

    @Test
    void fullProcessIntegrationTest() throws Exception {
        String deviceId = "device-" + UUID.randomUUID();
        String eventId = "event-" + UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        String type = "impedance";
        String payload = "{\"value\":15.5}";

        GenericRecord deviceEvent = new GenericData.Record(deviceEventSchema);
        deviceEvent.put("eventId", eventId);
        deviceEvent.put("deviceId", deviceId);
        deviceEvent.put("timestamp", timestamp);
        deviceEvent.put("type", type);
        deviceEvent.put("payload", payload);

        // 1. Отправить DeviceEvent в events.v1
        SendResult<String, GenericRecord> sendResult = deviceKafkaTemplate.send(EVENTS_TOPIC, deviceId, deviceEvent)
                .get(10, TimeUnit.SECONDS);
        assertThat(sendResult.getRecordMetadata().topic()).isEqualTo(EVENTS_TOPIC);

        // 2. Дождаться обработки listener'ом и появления записи в device_events
        await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT count() FROM collector.device_events WHERE device_id = ? AND event_id = ?",
                    Long.class, deviceId, eventId);
            assertThat(count).isNotNull().isGreaterThan(0);
        });

        // 3. Новый device_id → запись должна появиться в device_outbox
        await().atMost(5, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT count() FROM collector.device_outbox WHERE device_id = ?",
                    Long.class, deviceId);
            assertThat(count).isNotNull().isGreaterThan(0);
        });

        // 4. Зарегистрировать инстанс в Redis (heartbeat), чтобы processOutbox взял шард
        instanceRegistry.heartbeat();

        // 5. Подписаться на devices.v1 до вызова processOutbox (чтобы не пропустить сообщение)
        try (var consumer = kafkaAvroConsumerFactory.createConsumer("test-devices-consumer-" + UUID.randomUUID(), null)) {
            consumer.subscribe(java.util.Collections.singletonList(DEVICES_TOPIC));
            consumer.poll(Duration.ofSeconds(2));

            outboxProcessor.processOutbox();

            var polledConsumerRecords = consumer.poll(CONSUMER_POLL_TIMEOUT);
            assertThat(polledConsumerRecords).isNotEmpty();
            var deviceRecord = StreamSupport.stream(polledConsumerRecords.records(DEVICES_TOPIC).spliterator(), false)
                    .filter(r -> deviceId.equals(r.key()))
                    .findFirst();
            assertThat(deviceRecord).isPresent();
            GenericRecord value = deviceRecord.get().value();
            assertThat(value).isNotNull();
            assertThat(value.get("deviceId")).isNotNull();
            assertThat(value.get("deviceId").toString()).isEqualTo(deviceId);
        }

        // 6. Записи с этим device_id в outbox больше нет
        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT count() FROM collector.device_outbox WHERE device_id = ?",
                Long.class, deviceId);
        assertThat(outboxCount).isZero();
    }
}
