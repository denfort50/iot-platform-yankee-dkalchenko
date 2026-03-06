package ru.dkalchenko.device.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import ru.dkalchenko.device.properties.AuditProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka producer для отправки DeviceChangeRequest в devices.v1
 * и событий аудита (Avro) в топик из device-service.audit. Топик аудита создаётся явно при старте.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaConfig {

	KafkaProperties kafkaProperties;
	AuditProperties auditProperties;

	@Bean
	public ProducerFactory<String, GenericRecord> avroProducerFactory() {
		Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
		String schemaRegistryUrl = kafkaProperties.getProperties().getOrDefault("schema.registry.url", "http://localhost:8081");
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
		props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
		return new DefaultKafkaProducerFactory<>(props);
	}

	@Bean
	public KafkaTemplate<String, GenericRecord> deviceKafkaTemplate() {
		return new KafkaTemplate<>(avroProducerFactory());
	}

	@Bean
	public KafkaTemplate<String, GenericRecord> auditKafkaTemplate() {
		return new KafkaTemplate<>(avroProducerFactory());
	}

	@Bean
	public NewTopic auditDeviceEventsTopic() {
		return TopicBuilder.name(auditProperties.getTopic())
				.partitions(auditProperties.getTopicPartitions())
				.replicas((short) auditProperties.getTopicReplicas())
				.build();
	}
}
