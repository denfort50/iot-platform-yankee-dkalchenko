package ru.dkalchenko.device.audit;

import lombok.Builder;
import lombok.With;

import java.time.Instant;

/**
 * Неизменяемое событие аудита операции над устройством.
 * Содержит все данные, необходимые для расследования и соответствия требованиям.
 */
@With
@Builder(toBuilder = true)
public record DeviceAuditEvent(
		Instant timestamp,
		DeviceOperation operation,
		String deviceId,
		AuditOutcome outcome,
		String actor,
		String traceId,
		String errorMessage
) {
	public DeviceAuditEvent {
		if (timestamp == null) {
			timestamp = Instant.now();
		}
	}
}
