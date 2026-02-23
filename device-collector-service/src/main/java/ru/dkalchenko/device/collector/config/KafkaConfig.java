package ru.dkalchenko.device.collector.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
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
import org.springframework.util.StreamUtils;
import ru.dkalchenko.device.collector.metrics.CollectorMetrics;
import ru.dkalchenko.device.collector.properties.DeviceCollectorProperties;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Конфигурация Kafka для работы с Avro схемами через Schema Registry.
 * Включает поддержку Dead Letter Topic для обработки ошибок.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaConfig {

	private static final Schema DLT_MESSAGE_SCHEMA = loadDeadLetterMessageSchema();

	KafkaProperties kafkaProperties;
	DeviceCollectorProperties deviceCollectorProperties;
	CollectorMetrics collectorMetrics;

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
			GenericRecord dltMessage = buildDeadLetterMessage(record, ex);
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

	/**
	 * Собирает Avro-запись DeadLetterMessage по схеме DeadLetterMessage.avsc.
	 */
	private GenericRecord buildDeadLetterMessage(ConsumerRecord<?, ?> record, Exception ex) {
		GenericRecord msg = new GenericData.Record(DLT_MESSAGE_SCHEMA);
		msg.put("id", UUID.randomUUID().toString());
		msg.put("service", "device-collector-service");
		msg.put("originalTopic", record.topic());
		msg.put("timestamp", System.currentTimeMillis());
		msg.put("errorMessage", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
		msg.put("traceId", getTraceIdFromMdc());
		msg.put("originalKey", record.key() != null ? record.key().toString() : null);
		msg.put("originalValue", originalValueToJson(record.value()));
		return msg;
	}

	/**
	 * Читает traceId из MDC.
	 */
	private static String getTraceIdFromMdc() {
		String traceId = MDC.get("traceId");
		return traceId != null ? traceId : "";
	}

	private static String originalValueToJson(Object value) {
        switch (value) {
            case null -> {
                return "";
            }
            case GenericRecord genericRecord -> {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(genericRecord.getSchema());
                    JsonEncoder encoder = EncoderFactory.get().jsonEncoder(genericRecord.getSchema(), out);
                    writer.write(genericRecord, encoder);
                    encoder.flush();
                    return out.toString(StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return "{\"serializationError\":\"" + e.getMessage() + "\"}";
                }
            }
            case byte[] bytes -> {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            default -> {
            }
        }
        return value.toString();
	}

	private static Schema loadDeadLetterMessageSchema() {
		try {
			return new Schema.Parser().parse(
					StreamUtils.copyToString(
							new ClassPathResource("avro/DeadLetterMessage.avsc").getInputStream(),
							StandardCharsets.UTF_8
					)
			);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load DeadLetterMessage.avsc", e);
		}
	}
}
