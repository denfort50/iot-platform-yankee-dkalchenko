package ru.dkalchenko.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.event.exception.BadRequestException;
import ru.dkalchenko.event.exception.EventNotFoundException;
import ru.dkalchenko.event.repository.DeviceEventRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventReadServiceTest {

	private DeviceEventRepository repository;
	private EventReadService service;

	@BeforeEach
	void setUp() {
		repository = mock(DeviceEventRepository.class);
		service = new EventReadService(repository);
	}

	@Test
	void whenListEventsAndWhenDeviceDoesNotExistThenThrowsNotFound() {
		when(repository.existsDevice("missing-device")).thenReturn(false);

		assertThatThrownBy(() -> service.listEvents("missing-device", null, null, null, 0, 100))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessageContaining("Device not found");

		verify(repository, never()).count(eq("missing-device"), eq(null), eq(null), eq(null));
		verify(repository, never()).findPage(eq("missing-device"), eq(null), eq(null), eq(null), anyInt(), anyInt());
	}

	@Test
	void whenListEventsAndWhenDeviceExistsThenReturnsPage() {
		Event event = new Event()
				.eventId("e1")
				.deviceId("d1")
				.timestamp(1L)
				.type("t")
				.payload("{}");
		when(repository.existsDevice("d1")).thenReturn(true);
		when(repository.count("d1", null, null, null)).thenReturn(1L);
		when(repository.findPage("d1", null, null, null, 0, 100)).thenReturn(List.of(event));

		EventPage page = service.listEvents("d1", null, null, null, 0, 100);

		assertThat(page.getTotal()).isEqualTo(1L);
		assertThat(page.getEvents()).hasSize(1);
		assertThat(page.getEvents().getFirst().getEventId()).isEqualTo("e1");
		assertThat(page.getPage()).isEqualTo(0);
		assertThat(page.getSize()).isEqualTo(100);
	}

	@Test
	void whenListEventsAndWhenPageAndSizeMissingThenUsesDefaults() {
		when(repository.existsDevice("d1")).thenReturn(true);
		when(repository.count("d1", null, null, null)).thenReturn(0L);
		when(repository.findPage("d1", null, null, null, 0, 100)).thenReturn(List.of());

		EventPage page = service.listEvents("d1", null, null, null, null, null);

		assertThat(page.getPage()).isEqualTo(0);
		assertThat(page.getSize()).isEqualTo(100);
		verify(repository).count("d1", null, null, null);
		verify(repository).findPage("d1", null, null, null, 0, 100);
	}

	@Test
	void whenListEventsAndWhenTimeRangeInvalidThenThrowsBadRequest() {
		when(repository.existsDevice("d1")).thenReturn(true);

		assertThatThrownBy(() -> service.listEvents("d1", 10L, 1L, null, 0, 100))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("fromTimestamp must be less than or equal to toTimestamp");

		verify(repository, never()).count(eq("d1"), anyLong(), anyLong(), isNull());
		verify(repository, never()).findPage(eq("d1"), anyLong(), anyLong(), isNull(), anyInt(), anyInt());
	}

	@Test
	void whenListEventsAndWhenPageNegativeThenThrowsBadRequest() {
		when(repository.existsDevice("d1")).thenReturn(true);

		assertThatThrownBy(() -> service.listEvents("d1", null, null, null, -1, 100))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("page must be >= 0");
	}

	@Test
	void whenListEventsAndWhenSizeOutOfRangeThenThrowsBadRequest() {
		when(repository.existsDevice("d1")).thenReturn(true);

		assertThatThrownBy(() -> service.listEvents("d1", null, null, null, 0, 0))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("size must be between 1 and 1000");

		assertThatThrownBy(() -> service.listEvents("d1", null, null, null, 0, 1001))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("size must be between 1 and 1000");
	}

	@Test
	void whenListEventsAndWhenDeviceIdBlankThenThrowsBadRequest() {
		assertThatThrownBy(() -> service.listEvents(" ", null, null, null, 0, 100))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("deviceId is required");
	}

	@Test
	void whenGetEventAndWhenFoundThenReturnsEvent() {
		Event event = new Event()
				.eventId("e1")
				.deviceId("d1")
				.timestamp(1L)
				.type("temperature")
				.payload("{\"value\": 10}");
		when(repository.findByDeviceAndEventId("d1", "e1")).thenReturn(Optional.of(event));

		Event result = service.getEvent("d1", "e1");

		assertThat(result.getEventId()).isEqualTo("e1");
		assertThat(result.getDeviceId()).isEqualTo("d1");
	}

	@Test
	void whenGetEventAndWhenEventMissingThenThrowsNotFound() {
		when(repository.findByDeviceAndEventId("d1", "e404")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getEvent("d1", "e404"))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessageContaining("Event not found");
	}

	@Test
	void whenGetEventAndWhenEventIdBlankThenThrowsBadRequest() {
		assertThatThrownBy(() -> service.getEvent("d1", " "))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("eventId is required");
	}

	@Test
	void whenGetEventAndWhenDeviceIdBlankThenThrowsBadRequest() {
		assertThatThrownBy(() -> service.getEvent("", "e1"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("deviceId is required");
	}
}
