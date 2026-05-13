package ru.dkalchenko.orchestrator.adapter.inbound.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.adapter.inbound.api.generated.EventsApiDelegate;
import ru.dkalchenko.orchestrator.api.model.Event;
import ru.dkalchenko.orchestrator.api.model.EventPage;
import ru.dkalchenko.orchestrator.application.model.EventPageResult;
import ru.dkalchenko.orchestrator.application.model.EventView;
import ru.dkalchenko.orchestrator.application.port.in.EventUseCase;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventProxyController implements EventsApiDelegate {

	EventUseCase events;

	@Override
	public Mono<ResponseEntity<EventPage>> listEvents(String deviceId, Long fromTimestamp, Long toTimestamp, String type,
			Integer page, Integer size, ServerWebExchange exchange) {
		return events.listEvents(deviceId, fromTimestamp, toTimestamp, type, page, size)
				.doOnSubscribe(s -> log.debug(
						"listEvents deviceId={}, from={}, to={}, type={}, page={}, size={}",
						deviceId, fromTimestamp, toTimestamp, type, page, size))
				.map(EventProxyController::toEventPage)
				.map(ResponseEntity::ok);
	}

	@Override
	public Mono<ResponseEntity<Event>> getEventById(String eventId, String deviceId, ServerWebExchange exchange) {
		return events.getEventById(eventId, deviceId)
				.doOnSubscribe(s -> log.debug("getEventById eventId={}, deviceId={}", eventId, deviceId))
				.map(EventProxyController::toEvent)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	private static EventPage toEventPage(EventPageResult result) {
		return new EventPage()
				.page(result.page())
				.size(result.size())
				.total(result.total())
				.events(result.events().stream().map(EventProxyController::toEvent).toList());
	}

	private static Event toEvent(EventView view) {
		return new Event()
				.eventId(view.eventId())
				.deviceId(view.deviceId())
				.timestamp(view.timestamp())
				.type(view.type())
				.payload(view.payload());
	}
}
