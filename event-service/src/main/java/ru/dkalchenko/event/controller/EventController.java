package ru.dkalchenko.event.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.event.api.server.EventsApiDelegate;
import ru.dkalchenko.event.service.EventReadService;

/**
 * Реализация контракта OpenAPI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventController implements EventsApiDelegate {

	EventReadService eventReadService;

	@Override
	public ResponseEntity<EventPage> listEvents(String deviceId, Long fromTimestamp, Long toTimestamp,
												String type, Integer page, Integer size) {
		EventPage body = eventReadService.listEvents(deviceId, fromTimestamp, toTimestamp, type, page, size);
		return ResponseEntity.ok(body);
	}

	@Override
	public ResponseEntity<Event> getEventById(String eventId, String deviceId) {
		Event body = eventReadService.getEvent(deviceId, eventId);
		return ResponseEntity.ok(body);
	}
}
