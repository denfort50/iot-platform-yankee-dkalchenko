package ru.dkalchenko.collector.model;

import java.time.LocalDateTime;

/**
 * Запись outbox для последующей публикации устройства в devices.v1.
 * Поля traceId и spanId хранят контекст трейса на момент записи в outbox,
 * чтобы при публикации продолжить тот же сквозной трейс (events.v1 → devices.v1).
 */
public record DeviceOutbox(
        String deviceId,
        LocalDateTime createdAt,
        Integer attempts,
        String lastError,
        String traceId,
        String spanId
) {
    public DeviceOutbox(DeviceOutbox base, String traceId, String spanId) {
        this(base.deviceId(), base.createdAt(), base.attempts(), base.lastError(), traceId, spanId);
    }
}
