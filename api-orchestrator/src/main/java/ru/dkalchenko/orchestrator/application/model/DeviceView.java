package ru.dkalchenko.orchestrator.application.model;

import java.time.OffsetDateTime;

public record DeviceView(
		String deviceId,
		String deviceType,
		Long createdAt,
		String meta,
		OffsetDateTime updatedAt,
		String targetSoftwareVersion
) {
}
