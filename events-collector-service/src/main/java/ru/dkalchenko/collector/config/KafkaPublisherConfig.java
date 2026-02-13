package ru.dkalchenko.collector.config;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import ru.dkalchenko.collector.properties.OutboxProperties;

import java.util.Map;

/**
 * Конфигурация producer для публикации устройств (GenericRecord) в топик devices.v1.
 * Использует {@link KafkaProperties} (spring.kafka.*); сериализация через Schema Registry (KafkaAvroSerializer).
 * При старте создаётся топик из {@link OutboxProperties#kafkaTopic()} (если его ещё нет).
 */
@Configuration
public class KafkaPublisherConfig {

    @Bean
    public ProducerFactory<String, GenericRecord> deviceProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(producerProps);
    }

    @Bean
    public KafkaTemplate<String, GenericRecord> deviceKafkaTemplate(
            ProducerFactory<String, GenericRecord> deviceProducerFactory) {
        return new KafkaTemplate<>(deviceProducerFactory);
    }

    @Bean
    public NewTopic outboxKafkaTopic(OutboxProperties outboxProperties) {
        return TopicBuilder.name(outboxProperties.kafkaTopic())
                .partitions(outboxProperties.topicPartitions())
                .replicas(outboxProperties.topicReplicas())
                .build();
    }
}
