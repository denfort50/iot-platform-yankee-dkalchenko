package ru.dkalchenko.collector.model;

import java.time.LocalDateTime;

public record DeviceOutbox(
        String deviceId,
        LocalDateTime createdAt,
        Integer attempts,
        String lastError
) {
}
