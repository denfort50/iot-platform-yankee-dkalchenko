package ru.dkalchenko.orchestrator.adapter.outbound.event;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.orchestrator.adapter.outbound.feign.EventServiceFeignClient;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientEventOutboundAdapterTest {

	@Mock
	EventServiceFeignClient client;

	ResilientEventOutboundAdapter adapter;

	@BeforeEach
	void setUp() {
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
		TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(
				TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(30)).build());
		adapter = new ResilientEventOutboundAdapter(client, cbRegistry, tlRegistry);
	}

	private static Context bearerContext() {
		return Context.of(ReactiveBearerContext.KEY, "jwt");
	}

	@Test
	void whenListEventsAndWhenDeviceMissingThenReturnsDeviceNotFound() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(404);
		when(client.listEvents(eq("dev"), isNull(), isNull(), isNull(), isNull(), isNull())).thenThrow(ex);

		StepVerifier.create(adapter.listEvents("dev", null, null, null, null, null).contextWrite(bearerContext()))
				.expectError(DeviceNotFoundException.class)
				.verify();
	}

	@Test
	void whenListEventsAndWhenPageNullAndEventsNullThenMapsToEmptyList() {
		EventPage page = new EventPage().page(0).size(10).total(0L);
		page.setEvents(null);
		when(client.listEvents(eq("dev"), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

		StepVerifier.create(adapter.listEvents("dev", null, null, null, null, null).contextWrite(bearerContext()))
				.assertNext(r -> {
					assertThat(r.events()).isEmpty();
					assertThat(r.total()).isZero();
				})
				.verifyComplete();
	}

	@Test
	void whenGetEventByIdAndWhen404ThenReturnsEmpty() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(404);
		when(client.getEventById(eq("e1"), eq("dev"))).thenThrow(ex);

		StepVerifier.create(adapter.getEventById("e1", "dev").contextWrite(bearerContext()))
				.verifyComplete();
	}

	@Test
	void whenGetEventByIdAndWhen500ThenPropagates() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(500);
		when(client.getEventById(eq("e1"), eq("dev"))).thenThrow(ex);

		StepVerifier.create(adapter.getEventById("e1", "dev").contextWrite(bearerContext()))
				.expectError(FeignException.class)
				.verify();
	}

	@Test
	void whenGetEventByIdAndWhenOkThenMapsView() {
		Event ev = new Event()
				.eventId("e1")
				.deviceId("dev")
				.timestamp(42L)
				.type("T")
				.payload("{}");
		when(client.getEventById(eq("e1"), eq("dev"))).thenReturn(ev);

		StepVerifier.create(adapter.getEventById("e1", "dev").contextWrite(bearerContext()))
				.assertNext(v -> {
					assertThat(v.eventId()).isEqualTo("e1");
					assertThat(v.type()).isEqualTo("T");
				})
				.verifyComplete();
	}
}
