package ru.dkalchenko.device.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.properties.DeviceProperties;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Публикует запросы на изменение устройств в топик devices.v1.
 * Для каждой операции создаётся спан (create-device-kafka / update-device-kafka / delete-device-kafka)
 * и trace_id передаётся в заголовке сообщения Kafka.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DevicePublisher {

	static int SEND_TIMEOUT_SECONDS = 10;
	static String INSTRUMENTATION_SCOPE = "ru.dkalchenko.device";

	static String SPAN_CREATE = "create-device-kafka";
	static String SPAN_UPDATE = "update-device-kafka";
	static String SPAN_DELETE = "delete-device-kafka";

	static String OP_CREATE = "CREATE";
	static String OP_UPDATE = "UPDATE";
	static String OP_DELETE = "DELETE";

	OpenTelemetry openTelemetry;
	KafkaTemplate<String, GenericRecord> deviceKafkaTemplate;
	DeviceProperties properties;
	Schema deviceInfoSchema;

	public DevicePublisher(OpenTelemetry openTelemetry,
			KafkaTemplate<String, GenericRecord> deviceKafkaTemplate,
			DeviceProperties properties) {
		this.openTelemetry = openTelemetry;
		this.deviceKafkaTemplate = deviceKafkaTemplate;
		this.properties = properties;
		this.deviceInfoSchema = loadSchema();
		logDeviceInfoSchemaFields();
	}

	public void sendCreate(Device device) {
		GenericRecord record = new GenericData.Record(deviceInfoSchema);
		record.put("operation", OP_CREATE);
		record.put("deviceId", device.getDeviceId());
		record.put("deviceType", device.getDeviceType());
		record.put("createdAt", device.getCreatedAt());
		record.put("meta", device.getMeta());
		sendWithSpan(device.getDeviceId(), record, SPAN_CREATE);
	}

	public void sendUpdate(String deviceId, String deviceType, String meta) {
		GenericRecord record = new GenericData.Record(deviceInfoSchema);
		record.put("operation", OP_UPDATE);
		record.put("deviceId", deviceId);
		record.put("deviceType", deviceType);
		record.put("createdAt", null);
		record.put("meta", meta);
		sendWithSpan(deviceId, record, SPAN_UPDATE);
	}

	public void sendDelete(String deviceId) {
		GenericRecord record = new GenericData.Record(deviceInfoSchema);
		record.put("operation", OP_DELETE);
		record.put("deviceId", deviceId);
		record.put("deviceType", null);
		record.put("createdAt", null);
		record.put("meta", null);
		sendWithSpan(deviceId, record, SPAN_DELETE);
	}

	private void sendWithSpan(String key, GenericRecord record, String spanName) {
		var tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
		Span span = tracer.spanBuilder(spanName)
				.setAttribute("device.id", key)
				.setAttribute("messaging.destination", "devices.v1")
				.startSpan();
		try (Scope _ = span.makeCurrent()) {
			String traceId = span.getSpanContext().getTraceId();
			send(key, record, traceId);
		} finally {
			span.end();
		}
	}

	private void send(String key, GenericRecord record, String traceId) {
		String topic = properties.getKafka().getDeviceTopic();
		ProducerRecord<String, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, key, record);
		if (traceId != null && !traceId.isBlank()) {
			kafkaRecord.headers().add("trace_id", traceId.getBytes(StandardCharsets.UTF_8));
		}
		try {
			var result = deviceKafkaTemplate.send(kafkaRecord).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (result != null && log.isDebugEnabled()) {
				log.debug("Device change sent to Kafka: key={}, operation={}, partition={}, offset={}",
						key, record.get("operation"),
						result.getRecordMetadata().partition(),
						result.getRecordMetadata().offset());
			}
		} catch (ExecutionException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			log.error("Failed to send device change to Kafka: key={}, operation={}",
					key, record.get("operation"), cause);
			throw cause instanceof RuntimeException re ? re : new IllegalStateException("Kafka send failed", cause);
		} catch (TimeoutException e) {
			log.error("Timeout sending device change to Kafka: key={}, operation={}", key, record.get("operation"), e);
			throw new IllegalStateException("Kafka send timeout", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted while sending device change to Kafka: key={}, operation={}", key, record.get("operation"), e);
			throw new IllegalStateException("Kafka send interrupted", e);
		}
	}

	private static Schema loadSchema() {
		try {
			String json = StreamUtils.copyToString(
					new ClassPathResource("avro/DeviceInfo.avsc").getInputStream(),
					StandardCharsets.UTF_8
			);
			return new Schema.Parser().parse(json);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load DeviceInfo.avsc", e);
		}
	}

	private void logDeviceInfoSchemaFields() {
		var fields = deviceInfoSchema.getFields().stream()
				.map(Schema.Field::name)
				.toList();
		log.info("DeviceInfo schema loaded from classpath, fields: {}", fields);
	}
}
