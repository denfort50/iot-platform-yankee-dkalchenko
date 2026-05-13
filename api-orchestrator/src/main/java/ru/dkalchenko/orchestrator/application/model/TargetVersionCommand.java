package ru.dkalchenko.orchestrator.application.model;

public record TargetVersionCommand(
		String targetVersion,
		String idempotencyKey
) {
}
