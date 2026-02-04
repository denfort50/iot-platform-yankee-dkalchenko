package ru.dkalchenko.collector.model;

/**
 * Модель события от IoT-устройства.
 */
public record DeviceEvent(
        String eventId,
        String deviceId,
        Long timestamp,
        String type,
        String payload
) {
}
