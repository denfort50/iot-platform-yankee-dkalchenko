package ru.dkalchenko.collector.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import ru.dkalchenko.collector.properties.KafkaListenerTopicProperties;

import org.apache.avro.generic.GenericRecord;

import java.util.Map;

/**
 * Конфигурация Kafka для работы с Avro схемами через Schema Registry.
 * Consumer получает GenericRecord; парсинг в DeviceEvent — в EventSubscriber.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaListenerConfig {

    KafkaProperties kafkaProperties;
    KafkaListenerTopicProperties listenerTopicProperties;

    @Bean
    public ConsumerFactory<String, GenericRecord> kafkaAvroConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        String schemaRegistryUrl = kafkaProperties.getProperties()
                .getOrDefault("schema.registry.url", "http://localhost:8081");
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        if (kafkaProperties.getProperties().containsKey("specific.avro.reader")) {
            props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, 
                    Boolean.parseBoolean(kafkaProperties.getProperties().get("specific.avro.reader")));
        }
        if (kafkaProperties.getProperties().containsKey("auto.register.schemas")) {
            props.put(KafkaAvroDeserializerConfig.AUTO_REGISTER_SCHEMAS, 
                    Boolean.parseBoolean(kafkaProperties.getProperties().get("auto.register.schemas")));
        }
        if (kafkaProperties.getProperties().containsKey("use.latest.version")) {
            props.put(KafkaAvroDeserializerConfig.USE_LATEST_VERSION, 
                    Boolean.parseBoolean(kafkaProperties.getProperties().get("use.latest.version")));
        }
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GenericRecord> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaAvroConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean
    public NewTopic eventsRawTopic() {
        return TopicBuilder.name(listenerTopicProperties.kafkaTopic())
                .partitions(listenerTopicProperties.topicPartitions())
                .replicas(listenerTopicProperties.topicReplicas())
                .build();
    }
}
