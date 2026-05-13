package ru.dkalchenko.orchestrator.application.model;

import jakarta.validation.constraints.NotBlank;

public record DeviceVersionUpdateRequest(
		@NotBlank String targetVersion,
		@NotBlank String routerId,
		String idempotencyKey
) {
}
