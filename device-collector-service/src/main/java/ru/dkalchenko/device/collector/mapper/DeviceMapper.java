package ru.dkalchenko.device.collector.mapper;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;
import ru.dkalchenko.device.collector.model.Device;
import ru.dkalchenko.device.collector.model.DeviceUpdate;

import java.time.Instant;

/**
 * Маппер для преобразования GenericRecord из Kafka в Entity Device / DeviceUpdate.
 * Поддерживает поле operation (CREATE, UPDATE, DELETE) из схемы DeviceInfo.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceMapper {

	public static final String OP_CREATE = "CREATE";
	public static final String OP_UPDATE = "UPDATE";
	public static final String OP_DELETE = "DELETE";

	/**
	 * Возвращает значение поля operation из записи. По умолчанию CREATE.
	 */
	public String getOperation(GenericRecord record) {
		if (record == null) return OP_CREATE;
		String op = getStringValue(record, "operation");
		return op != null && !op.isBlank() ? op.trim().toUpperCase() : OP_CREATE;
	}

	/**
	 * Возвращает deviceId из записи. Для DELETE требуется только он.
	 */
	public String getDeviceId(GenericRecord record) {
		if (record == null) {
			throw new IllegalArgumentException("GenericRecord cannot be null");
		}
		String deviceId = getStringValue(record, "deviceId");
		if (deviceId == null || deviceId.isBlank()) {
			throw new IllegalArgumentException("deviceId is required and cannot be blank");
		}
		return deviceId;
	}

	/**
	 * Преобразует GenericRecord из Kafka в Device entity (для operation=CREATE).
	 *
	 * @param record Avro GenericRecord с полями: operation, deviceId, deviceType, createdAt, meta
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

	/**
	 * Извлекает данные для UPDATE из записи (deviceId обязателен, deviceType и meta могут быть null).
	 */
	public DeviceUpdate toDeviceForUpdate(GenericRecord record) {
		if (record == null) {
			throw new IllegalArgumentException("GenericRecord cannot be null");
		}
		String deviceId = getDeviceId(record);
		String deviceType = getStringValue(record, "deviceType");
		String meta = getStringValue(record, "meta");
		return new DeviceUpdate(deviceId, deviceType != null ? deviceType : "", meta != null ? meta : "");
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
