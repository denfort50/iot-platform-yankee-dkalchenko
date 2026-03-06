package ru.dkalchenko.device.collector.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import ru.dkalchenko.device.collector.dlt.DeadLetterMessageBuilder;
import ru.dkalchenko.device.collector.metrics.CollectorMetrics;
import ru.dkalchenko.device.collector.properties.DeviceCollectorProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka: consumer/producer factories, listener container, DLT topic.
 * Построение сообщений для DLT делегировано {@link DeadLetterMessageBuilder}.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaConfig {

    KafkaProperties kafkaProperties;
    DeviceCollectorProperties deviceCollectorProperties;
    CollectorMetrics collectorMetrics;
    DeadLetterMessageBuilder deadLetterMessageBuilder;

    @Bean
    public ConsumerFactory<String, GenericRecord> kafkaAvroConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        String schemaRegistryUrl = kafkaProperties.getProperties()
                .getOrDefault("schema.registry.url", "http://localhost:8081");

        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                Boolean.parseBoolean(kafkaProperties.getProperties()
                        .getOrDefault("specific.avro.reader", "true")));
        props.put(KafkaAvroDeserializerConfig.AUTO_REGISTER_SCHEMAS,
                Boolean.parseBoolean(kafkaProperties.getProperties()
                        .getOrDefault("auto.register.schemas", "true")));
        props.put(KafkaAvroDeserializerConfig.USE_LATEST_VERSION,
                Boolean.parseBoolean(kafkaProperties.getProperties()
                        .getOrDefault("use.latest.version", "true")));

        // Настройка ErrorHandlingDeserializer для обработки ошибок десериализации
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GenericRecord> kafkaListenerContainerFactory(
            KafkaTemplate<String, GenericRecord> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaAvroConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Отправка в DLT в формате DeadLetterMessage (Avro)
        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            GenericRecord dltMessage = deadLetterMessageBuilder.build(record, ex);
            String dltTopic = deviceCollectorProperties.getPublisher().getDltTopic();
            String key = record.key() != null ? record.key().toString() : null;
            kafkaTemplate.send(dltTopic, key, dltMessage);
            if (key != null) {
                collectorMetrics.incrementDevicesSentToDlt(key);
            }
        };

        // Обработчик ошибок: после 3 попыток отправляем в DLT
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L) // Задержка 1 секунда, максимум 3 попытки
        );

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public ProducerFactory<String, GenericRecord> kafkaAvroProducerFactory() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        String schemaRegistryUrl = kafkaProperties.getProperties()
                .getOrDefault("schema.registry.url", "http://localhost:8081");

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                io.confluent.kafka.serializers.KafkaAvroSerializer.class);
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, GenericRecord> kafkaTemplate() {
        return new KafkaTemplate<>(kafkaAvroProducerFactory());
    }

    @Bean
    public NewTopic devicesDltTopic() {
        return TopicBuilder.name(deviceCollectorProperties.getPublisher().getDltTopic())
                .partitions(deviceCollectorProperties.getPublisher().getDltTopicPartitions())
                .replicas(deviceCollectorProperties.getPublisher().getDltTopicReplicas())
                .build();
    }
}
