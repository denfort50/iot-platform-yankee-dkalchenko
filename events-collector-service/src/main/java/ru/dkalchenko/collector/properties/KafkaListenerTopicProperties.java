package ru.dkalchenko.collector.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства топика Kafka для потребления сырых событий (events).
 */
@ConfigurationProperties(prefix = "collector.listener")
public record KafkaListenerTopicProperties(
        String kafkaTopic,
        int topicPartitions,
        short topicReplicas
) {
    private static final String DEFAULT_KAFKA_TOPIC = "events.v1";
    private static final int DEFAULT_TOPIC_PARTITIONS = 3;
    private static final short DEFAULT_TOPIC_REPLICAS = 2;

    public KafkaListenerTopicProperties {
        if (kafkaTopic == null || kafkaTopic.isBlank()) {
            kafkaTopic = DEFAULT_KAFKA_TOPIC;
        }
        if (topicPartitions <= 0) {
            topicPartitions = DEFAULT_TOPIC_PARTITIONS;
        }
        if (topicReplicas <= 0) {
            topicReplicas = DEFAULT_TOPIC_REPLICAS;
        }
    }
}
