package ru.dkalchenko.device.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import ru.dkalchenko.device.properties.AuditProperties;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реализация аудита: структурированное логирование и отправка в Kafka (Avro) в топик из конфигурации.
 * Запись выполняется асинхронно; сбой Kafka не влияет на основной поток.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceAuditServiceImpl implements DeviceAuditService {

	private static final String AUDIT_LOGGER_NAME = "device.audit";
	private static final String AVRO_SCHEMA_RESOURCE = "avro/DeviceAuditEvent.avsc";

	ObjectMapper objectMapper;
	KafkaTemplate<String, GenericRecord> auditKafkaTemplate;
	AuditProperties auditProperties;
	Schema auditEventSchema;

	public DeviceAuditServiceImpl(
			ObjectMapper objectMapper,
			@Qualifier("auditKafkaTemplate") KafkaTemplate<String, GenericRecord> auditKafkaTemplate,
			AuditProperties auditProperties) {
		this.objectMapper = objectMapper;
		this.auditKafkaTemplate = auditKafkaTemplate;
		this.auditProperties = auditProperties;
		this.auditEventSchema = loadSchema();
	}

	@Override
	@Async
	public void record(DeviceAuditEvent event) {
		String json;
		try {
			json = toJson(event);
		} catch (JsonProcessingException e) {
			log.warn("Failed to serialize audit event: {}", event, e);
			return;
		}
		org.slf4j.LoggerFactory.getLogger(AUDIT_LOGGER_NAME).info("{}", json);

		GenericRecord record = toAvroRecord(event);
		String topic = auditProperties.getTopic();
		auditKafkaTemplate.send(topic, event.deviceId(), record)
				.whenComplete((SendResult<String, GenericRecord> result, Throwable ex) -> {
					if (ex != null) {
						log.warn("Failed to send audit event to Kafka: topic={}, deviceId={}, operation={}",
								topic, event.deviceId(), event.operation(), ex);
					} else if (log.isDebugEnabled() && result != null && result.getRecordMetadata() != null) {
						log.debug("Audit event sent: topic={}, partition={}, offset={}",
								topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
					}
				});
	}

	private GenericRecord toAvroRecord(DeviceAuditEvent event) {
		GenericRecord record = new GenericData.Record(auditEventSchema);
		record.put("timestamp", event.timestamp().toString());
		record.put("operation", event.operation().name());
		record.put("deviceId", event.deviceId());
		record.put("outcome", event.outcome().name());
		record.put("actor", event.actor());
		record.put("traceId", event.traceId() != null && !event.traceId().isBlank() ? event.traceId() : null);
		record.put("errorMessage", event.errorMessage() != null && !event.errorMessage().isBlank() ? event.errorMessage() : null);
		return record;
	}

	private static Schema loadSchema() {
		try {
			String json = StreamUtils.copyToString(
					new ClassPathResource(AVRO_SCHEMA_RESOURCE).getInputStream(),
					StandardCharsets.UTF_8
			);
			return new Schema.Parser().parse(json);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load " + AVRO_SCHEMA_RESOURCE, e);
		}
	}

	private String toJson(DeviceAuditEvent event) throws JsonProcessingException {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("timestamp", event.timestamp().toString());
		payload.put("operation", event.operation().name());
		payload.put("deviceId", event.deviceId());
		payload.put("outcome", event.outcome().name());
		payload.put("actor", event.actor());
		if (event.traceId() != null && !event.traceId().isBlank()) {
			payload.put("traceId", event.traceId());
		}
		if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
			payload.put("errorMessage", event.errorMessage());
		}
		return objectMapper.writeValueAsString(payload);
	}
}
