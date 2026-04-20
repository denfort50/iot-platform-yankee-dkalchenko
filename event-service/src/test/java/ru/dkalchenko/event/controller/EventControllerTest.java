package ru.dkalchenko.event.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.event.api.server.EventsApiController;
import ru.dkalchenko.event.api.server.EventsApiDelegate;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventsApiController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	EventsApiDelegate eventsApiDelegate;

	@Test
	@WithMockUser
	void whenListEventsThenSuccess() throws Exception {
		EventPage page = new EventPage()
				.events(List.of(new Event()
						.eventId("e1")
						.deviceId("d1")
						.timestamp(100L)
						.type("t")
						.payload("{}")))
				.page(0)
				.size(10)
				.total(1L);
		when(eventsApiDelegate.listEvents(eq("d1"), isNull(), isNull(), isNull(), eq(0), eq(100)))
				.thenReturn(ResponseEntity.ok(page));

		mockMvc.perform(get("/api/v1/events")
						.param("deviceId", "d1")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.events[0].eventId").value("e1"));
		verify(eventsApiDelegate).listEvents(eq("d1"), isNull(), isNull(), isNull(), eq(0), eq(100));
	}

	@Test
	@WithMockUser
	void whenGetEventByIdThenSuccess() throws Exception {
		Event ev = new Event()
				.eventId("e1")
				.deviceId("d1")
				.timestamp(1L)
				.type("x")
				.payload("{}");
		when(eventsApiDelegate.getEventById("e1", "d1")).thenReturn(ResponseEntity.ok(ev));

		mockMvc.perform(get("/api/v1/events/e1").param("deviceId", "d1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value("e1"));
	}

}
