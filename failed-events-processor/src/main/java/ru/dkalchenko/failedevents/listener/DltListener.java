package ru.dkalchenko.failedevents.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import ru.dkalchenko.failedevents.metrics.FailedEventsMetrics;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;
import ru.dkalchenko.failedevents.service.DltPayloadParser;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedDevice;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedEvent;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedPayload;
import ru.dkalchenko.failedevents.service.DltPayloadParser.ParsedUnknown;
import ru.dkalchenko.failedevents.service.MinioStorageService;
import ru.dkalchenko.failedevents.tracing.DltTraceContextRunner;

/**
 * Слушает Dead Letter Topic (dlt.v1), десериализует DeadLetterMessage,
 * парсит originalValue в DeviceEvent или DeviceInfo, формирует JSON и сохраняет в MinIO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DltListener {

	FailedEventsProcessorProperties properties;
	DltPayloadParser payloadParser;
	MinioStorageService minioStorageService;
	FailedEventsMetrics metrics;
	DltTraceContextRunner traceContextRunner;

	@KafkaListener(
			topics = "#{__listener.dltTopic}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void handleDltMessage(ConsumerRecord<String, GenericRecord> record, Acknowledgment acknowledgment) {
		String key = record.key() != null ? record.key() : "";
		GenericRecord value = record.value();
		if (value == null) {
			log.error("Received null DLT message: key={}, partition={}, offset={}", key, record.partition(), record.offset());
			metrics.incrementDltReceivedInvalid();
			acknowledgment.acknowledge();
			return;
		}

		String originalValueJson = getString(value, "originalValue");
		String errorSource = getString(value, "service");
		String errorMessage = getString(value, "errorMessage");
		String stackTrace = getString(value, "stackTrace");
		String traceId = getStringOrNull(value, "traceId");
		long receivedAt = System.currentTimeMillis();

		ParsedPayload parsed = payloadParser.parse(originalValueJson, errorSource, errorMessage, stackTrace, receivedAt);

		try {
			traceContextRunner.runInSpan(traceId, () -> {
				switch (parsed) {
					case ParsedEvent e -> {
						String objectName = minioStorageService.saveFailedEvent(e.json(), e.eventType());
						log.info("Saved failed event to MinIO: {}", objectName);
						metrics.incrementFailedEventSaved();
						acknowledgment.acknowledge();
					}
					case ParsedDevice d -> {
						String objectName = minioStorageService.saveFailedDevice(d.json(), d.eventType());
						log.info("Saved failed device to MinIO: {}", objectName);
						metrics.incrementFailedDeviceSaved();
						acknowledgment.acknowledge();
					}
					case ParsedUnknown u -> {
						log.warn("Unknown DLT payload, skipping: {}", u.error());
						metrics.incrementDltReceivedUnknown();
						acknowledgment.acknowledge();
					}
				}
			});
		} catch (Exception e) {
			log.error("Error processing DLT message: key={}, partition={}, offset={}", key, record.partition(), record.offset(), e);
			metrics.incrementProcessingError();
			throw new RuntimeException("Failed to process DLT message", e);
		}
	}

	public String getDltTopic() {
		return properties.getListener().getDltTopic();
	}

	private static String getString(GenericRecord record, String field) {
		Object v = record.get(field);
		return v == null ? "" : v.toString();
	}

	private static String getStringOrNull(GenericRecord record, String field) {
		Object v = record.get(field);
		return v == null ? null : (v.toString().isBlank() ? null : v.toString());
	}
}
