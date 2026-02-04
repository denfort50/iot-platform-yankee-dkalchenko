package ru.dkalchenko.collector.config;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация клиента Schema Registry для регистрации Avro-схем при старте.
 */
@Configuration
public class SchemaRegistryConfig {

    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
        String url = kafkaProperties.getProperties()
                .getOrDefault("schema.registry.url", "http://localhost:8081");
        return new CachedSchemaRegistryClient(url, 100);
    }
}
