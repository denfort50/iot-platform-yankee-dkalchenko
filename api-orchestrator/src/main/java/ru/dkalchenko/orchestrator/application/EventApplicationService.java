package ru.dkalchenko.orchestrator.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.EventPageResult;
import ru.dkalchenko.orchestrator.application.model.EventView;
import ru.dkalchenko.orchestrator.application.port.in.EventUseCase;
import ru.dkalchenko.orchestrator.domain.port.EventOutboundPort;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventApplicationService implements EventUseCase {

	EventOutboundPort events;

	@Override
	public Mono<EventPageResult> listEvents(String deviceId, Long fromTimestamp, Long toTimestamp, String type, Integer page, Integer size) {
		return events.listEvents(deviceId, fromTimestamp, toTimestamp, type, page, size)
				.doOnSubscribe(s -> log.debug("listEvents service deviceId={}", deviceId));
	}

	@Override
	public Mono<EventView> getEventById(String eventId, String deviceId) {
		return events.getEventById(eventId, deviceId)
				.doOnSubscribe(s -> log.debug("getEventById service eventId={}, deviceId={}", eventId, deviceId));
	}
}
