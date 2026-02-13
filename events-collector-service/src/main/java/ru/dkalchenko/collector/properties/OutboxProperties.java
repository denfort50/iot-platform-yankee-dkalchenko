package ru.dkalchenko.collector.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства фонового процесса outbox: расписание, топик Kafka, батч, ключ и TTL идемпотентности в Redis.
 */
@ConfigurationProperties(prefix = "collector.outbox")
public record OutboxProperties(
        String schedule,
        String kafkaTopic,
        int topicPartitions,
        short topicReplicas,
        int batchSize,
        String publishedKey,
        long publishedTtlSeconds
) {
    private static final String DEFAULT_SCHEDULE = "0 * * * * *";
    private static final String DEFAULT_KAFKA_TOPIC = "devices.v1";
    private static final int DEFAULT_TOPIC_PARTITIONS = 3;
    private static final short DEFAULT_TOPIC_REPLICAS = 2;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String DEFAULT_PUBLISHED_KEY = "devices:published";
    private static final long DEFAULT_PUBLISHED_TTL_SECONDS = 86400L * 7;

    public OutboxProperties {
        if (schedule == null || schedule.isBlank()) {
            schedule = DEFAULT_SCHEDULE;
        }
        if (kafkaTopic == null || kafkaTopic.isBlank()) {
            kafkaTopic = DEFAULT_KAFKA_TOPIC;
        }
        if (topicPartitions <= 0) {
            topicPartitions = DEFAULT_TOPIC_PARTITIONS;
        }
        if (topicReplicas <= 0) {
            topicReplicas = DEFAULT_TOPIC_REPLICAS;
        }
        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        if (publishedKey == null || publishedKey.isBlank()) {
            publishedKey = DEFAULT_PUBLISHED_KEY;
        }
        if (publishedTtlSeconds <= 0) {
            publishedTtlSeconds = DEFAULT_PUBLISHED_TTL_SECONDS;
        }
    }
}
