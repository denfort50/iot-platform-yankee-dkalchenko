package ru.dkalchenko.device.collector.model;

import java.time.Instant;

/**
 * Модель устройства (данные из Kafka / запись в БД).
 * Таблица collector.devices шардируется по device_id через Apache ShardingSphere.
 */
public record Device(
		String deviceId,
		String deviceType,
		Long createdAt,
		String meta,
		Instant updatedAt
) {
	public Device(String deviceId, String deviceType, Long createdAt, String meta, Instant updatedAt) {
		this.deviceId = deviceId;
		this.deviceType = deviceType;
		this.createdAt = createdAt;
		this.meta = meta;
		this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
	}
}
