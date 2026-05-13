package ru.dkalchenko.orchestrator.application.model;

public record RouterCommandMessage(
		String commandId,
		String commandType,
		String payload,
		String status
) {
}
