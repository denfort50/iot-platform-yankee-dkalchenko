package ru.dkalchenko.orchestrator.application.model;

public record EventView(
		String eventId,
		String deviceId,
		Long timestamp,
		String type,
		String payload
) {
}
