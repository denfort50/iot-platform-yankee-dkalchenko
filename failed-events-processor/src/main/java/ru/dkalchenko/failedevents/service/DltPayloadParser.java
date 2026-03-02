package ru.dkalchenko.failedevents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dkalchenko.failedevents.model.FailedDeviceJson;
import ru.dkalchenko.failedevents.model.FailedEventJson;

/**
 * Парсит originalValue из DeadLetterMessage (JSON) в FailedEventJson или FailedDeviceJson.
 * DeviceEvent.avsc содержит eventId, deviceId, timestamp, type, payload.
 * DeviceInfo.avsc содержит operation, deviceId, deviceType, createdAt, meta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DltPayloadParser {

	private static final String EVENT_ID = "eventId";
	private static final String OPERATION = "operation";

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Результат парсинга: либо событие (failed event), либо устройство (failed device).
	 */
	public sealed interface ParsedPayload permits ParsedEvent, ParsedDevice, ParsedUnknown {
	}

	public record ParsedEvent(FailedEventJson json, String eventType) implements ParsedPayload {
	}

	public record ParsedDevice(FailedDeviceJson json, String eventType) implements ParsedPayload {
	}

	public record ParsedUnknown(String rawJson, String error) implements ParsedPayload {
	}

	/**
	 * Парсит JSON-строку originalValue. Если есть поле eventId — считаем DeviceEvent;
	 * если operation — DeviceInfo. Иначе — unknown.
	 */
	public ParsedPayload parse(String originalValueJson, String errorSource, String errorMessage, String stackTrace, long receivedAt) {
		if (originalValueJson == null || originalValueJson.isBlank()) {
			return new ParsedUnknown(originalValueJson, "empty originalValue");
		}
		FailedEventJson.ErrorMeta errorMeta = FailedEventJson.ErrorMeta.builder()
				.errorSource(errorSource)
				.reason(errorMessage)
				.stackTrace(stackTrace)
				.build();

		try {
			JsonNode root = objectMapper.readTree(originalValueJson);
			if (root.has(EVENT_ID)) {
				FailedEventJson.FailedEvent failedEvent = FailedEventJson.FailedEvent.builder()
						.eventId(getText(root, "eventId"))
						.deviceId(getText(root, "deviceId"))
						.type(getText(root, "type"))
						.timestamp(getLong(root, "timestamp", 0L))
						.payload(getText(root, "payload"))
						.build();
				String eventType = getText(root, "type");
				if (eventType == null || eventType.isBlank()) {
					eventType = "unknown";
				}
				FailedEventJson json = FailedEventJson.builder()
						.failedEvent(failedEvent)
						.errorMeta(errorMeta)
						.receivedAt(receivedAt)
						.build();
				return new ParsedEvent(json, sanitizeForFilename(eventType));
			}
			if (root.has(OPERATION)) {
				String operation = getText(root, "operation");
				FailedDeviceJson.FailedDevice failedDevice = FailedDeviceJson.FailedDevice.builder()
						.deviceId(getText(root, "deviceId"))
						.deviceType(getText(root, "deviceType"))
						.createdAt(getLongOrNull(root, "createdAt"))
						.meta(getText(root, "meta"))
						.build();
				String eventType = operation != null && !operation.isBlank() ? operation : "device";
				FailedDeviceJson json = FailedDeviceJson.builder()
						.failedDevice(failedDevice)
						.errorMeta(errorMeta)
						.receivedAt(receivedAt)
						.build();
				return new ParsedDevice(json, sanitizeForFilename(eventType));
			}
			return new ParsedUnknown(originalValueJson, "unknown schema: no eventId nor operation");
		} catch (Exception e) {
			log.warn("Failed to parse originalValue as JSON: {}", e.getMessage());
			return new ParsedUnknown(originalValueJson, e.getMessage());
		}
	}

	private static String getText(JsonNode node, String field) {
		JsonNode n = node.get(field);
		return n == null || n.isNull() ? null : n.asText("");
	}

	private static long getLong(JsonNode node, String field, long defaultValue) {
		JsonNode n = node.get(field);
		if (n == null || n.isNull()) return defaultValue;
		return n.isNumber() ? n.asLong() : defaultValue;
	}

	private static Long getLongOrNull(JsonNode node, String field) {
		JsonNode n = node.get(field);
		if (n == null || n.isNull()) return null;
		return n.isNumber() ? n.asLong() : null;
	}

	private static String sanitizeForFilename(String s) {
		if (s == null) return "unknown";
		return s.replaceAll("[^a-zA-Z0-9_-]", "_");
	}
}
