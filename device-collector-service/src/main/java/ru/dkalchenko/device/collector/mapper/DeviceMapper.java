package ru.dkalchenko.device.collector.mapper;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;
import ru.dkalchenko.device.collector.model.Device;

import java.time.Instant;

/**
 * Маппер для преобразования GenericRecord из Kafka в Entity Device.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceMapper {

	/**
	 * Преобразует GenericRecord из Kafka в Device entity.
	 *
	 * @param record Avro GenericRecord с полями: deviceId, deviceType, createdAt, meta
	 * @return Device entity
	 */
	public Device toDevice(GenericRecord record) {
		if (record == null) {
			throw new IllegalArgumentException("GenericRecord cannot be null");
		}

		String deviceId = getStringValue(record, "deviceId");
		String deviceType = getStringValue(record, "deviceType");
		Long createdAt = getLongValue(record);
		String meta = getStringValue(record, "meta");

		if (deviceId == null || deviceId.isBlank()) {
			throw new IllegalArgumentException("deviceId is required and cannot be blank");
		}
		if (deviceType == null || deviceType.isBlank()) {
			throw new IllegalArgumentException("deviceType is required and cannot be blank");
		}
		if (createdAt == null) {
			throw new IllegalArgumentException("createdAt is required");
		}

		return new Device(deviceId, deviceType, createdAt, meta, Instant.now());
	}

	private String getStringValue(GenericRecord record, String fieldName) {
		Object value = record.get(fieldName);
		return value != null ? value.toString() : null;
	}

	private Long getLongValue(GenericRecord record) {
		Object value = record.get("createdAt");
		return switch (value) {
			case null -> null;
			case Long l -> l;
			case Integer i -> i.longValue();
			case Number number -> number.longValue();
			default -> throw new IllegalArgumentException(
					String.format("Field %s must be a number, but got: %s", "createdAt", value.getClass())
			);
		};
	}
}
