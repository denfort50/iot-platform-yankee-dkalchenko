package ru.dkalchenko.event.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.event.exception.BadRequestException;
import ru.dkalchenko.event.exception.EventNotFoundException;
import ru.dkalchenko.event.repository.DeviceEventRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventReadService {

	DeviceEventRepository deviceEventRepository;

	public EventPage listEvents(String deviceId, Long fromTimestamp, Long toTimestamp,
								String type, Integer page, Integer size) {
		validateDeviceId(deviceId);
		validateDeviceExists(deviceId);
		if (fromTimestamp != null && toTimestamp != null && fromTimestamp > toTimestamp) {
			throw new BadRequestException("fromTimestamp must be less than or equal to toTimestamp");
		}
		int p = page != null ? page : 0;
		int s = size != null ? size : 100;
		if (p < 0) {
			throw new BadRequestException("page must be >= 0");
		}
		if (s < 1 || s > 1000) {
			throw new BadRequestException("size must be between 1 and 1000");
		}
		long total = deviceEventRepository.count(deviceId, fromTimestamp, toTimestamp, type);
		int offset = p * s;
		List<Event> events = deviceEventRepository.findPage(deviceId, fromTimestamp, toTimestamp, type, offset, s);
		return new EventPage()
				.events(events)
				.page(p)
				.size(s)
				.total(total);
	}

	public Event getEvent(String deviceId, String eventId) {
		validateDeviceId(deviceId);
		if (eventId == null || eventId.isBlank()) {
			throw new BadRequestException("eventId is required");
		}
		return deviceEventRepository.findByDeviceAndEventId(deviceId, eventId)
				.orElseThrow(() -> new EventNotFoundException(
						"Event not found for deviceId=%s, eventId=%s".formatted(deviceId, eventId)));
	}

	private static void validateDeviceId(String deviceId) {
		if (deviceId == null || deviceId.isBlank()) {
			throw new BadRequestException("deviceId is required");
		}
	}

	private void validateDeviceExists(String deviceId) {
		if (!deviceEventRepository.existsDevice(deviceId)) {
			throw new EventNotFoundException("Device not found: deviceId=%s".formatted(deviceId));
		}
	}
}
