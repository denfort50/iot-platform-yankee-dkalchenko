package ru.dkalchenko.orchestrator.application.port.in;

import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.EventPageResult;
import ru.dkalchenko.orchestrator.application.model.EventView;

public interface EventUseCase {

	Mono<EventPageResult> listEvents(String deviceId, Long fromTimestamp, Long toTimestamp, String type, Integer page, Integer size);

	Mono<EventView> getEventById(String eventId, String deviceId);
}
