package ru.dkalchenko.orchestrator.application.model;

import java.util.List;

public record EventPageResult(
		List<EventView> events,
		Integer page,
		Integer size,
		Long total
) {
}
