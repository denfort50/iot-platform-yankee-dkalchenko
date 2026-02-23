package ru.dkalchenko.device.properties;

import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static lombok.AccessLevel.PRIVATE;

/**
 * Параметры настройки аудита: топик Kafka и его создание при старте.
 */
@Data
@ConfigurationProperties(prefix = "device-service.audit")
@FieldDefaults(level = PRIVATE)
public class AuditProperties {

    /**
     * Имя топика Kafka для событий аудита операций над устройствами.
     */
    String topic = "audit.device-events.v1";
    /**
     * Число партиций топика при создании.
     */
    int topicPartitions = 3;
    /**
     * Фактор репликации топика при создании.
     */
    int topicReplicas = 1;
}
