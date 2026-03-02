package ru.dkalchenko.failedevents.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;

import java.util.Map;

/**
 * Конфигурация Kafka consumer для топика dlt.v1 (DeadLetterMessage, Avro).
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaConfig {

	KafkaProperties kafkaProperties;

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

		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
		props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);

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
}
