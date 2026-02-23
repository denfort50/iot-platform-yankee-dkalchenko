package ru.dkalchenko.device.collector.model;

/**
 * Данные для операции UPDATE устройства из Kafka (DeviceInfo с operation=UPDATE).
 * deviceType и meta могут быть null в Avro; при записи в БД используются пустые строки при необходимости.
 */
public record DeviceUpdate(
		String deviceId,
		String deviceType,
		String meta
) {}
