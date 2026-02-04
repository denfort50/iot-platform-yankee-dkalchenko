package ru.dkalchenko.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.dkalchenko.collector.properties.OutboxProperties;

/**
 * Публикует сообщения об устройствах в топик Kafka (devices.v1).
 * Принимает готовый Avro GenericRecord; ключ сообщения — deviceId из записи.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DevicePublisher {

    KafkaTemplate<String, GenericRecord> deviceKafkaTemplate;
    OutboxProperties outboxProperties;

    /**
     * Отправляет устройство (GenericRecord по схеме DeviceDto) в топик devices.v1.
     * Ключ сообщения берётся из поля record.deviceId.
     *
     * @param record Avro GenericRecord с полем deviceId
     */
    public void publish(GenericRecord record) {
        String topic = outboxProperties.kafkaTopic();
        String key = record != null && record.get("deviceId") != null
                ? record.get("deviceId").toString()
                : "";
        deviceKafkaTemplate.send(topic, key, record);
        log.debug("Published device to {}: deviceId={}", topic, key);
    }
}
