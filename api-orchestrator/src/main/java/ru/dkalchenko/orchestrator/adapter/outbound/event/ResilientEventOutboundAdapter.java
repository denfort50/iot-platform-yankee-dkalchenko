package ru.dkalchenko.orchestrator.adapter.outbound.event;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import feign.FeignException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;
import ru.dkalchenko.orchestrator.config.ServiceJwtHolder;
import ru.dkalchenko.event.api.model.Event;
import ru.dkalchenko.event.api.model.EventPage;
import ru.dkalchenko.orchestrator.adapter.outbound.feign.EventServiceFeignClient;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.application.model.EventPageResult;
import ru.dkalchenko.orchestrator.application.model.EventView;
import ru.dkalchenko.orchestrator.domain.port.EventOutboundPort;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResilientEventOutboundAdapter implements EventOutboundPort {

	static String NAME = "eventService";

	EventServiceFeignClient client;
	CircuitBreaker circuitBreaker;
	TimeLimiter timeLimiter;

	public ResilientEventOutboundAdapter(EventServiceFeignClient client,
			io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry cbRegistry,
			io.github.resilience4j.timelimiter.TimeLimiterRegistry tlRegistry) {
		this.client = client;
		this.circuitBreaker = cbRegistry.circuitBreaker(NAME);
		this.timeLimiter = tlRegistry.timeLimiter(NAME);
	}

	@Override
	public Mono<EventPageResult> listEvents(String deviceId, Long fromTimestamp, Long toTimestamp, String type,
			Integer page, Integer size) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx,
						() -> client.listEvents(deviceId, fromTimestamp, toTimestamp, type, page, size)))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientEventOutboundAdapter::toEventPageResult)
				.doOnSubscribe(s -> log.debug("Outbound event-service listEvents deviceId={}, page={}, size={}", deviceId, page, size))
				.doOnError(FeignException.class,
						e -> log.warn("Outbound event-service listEvents failed deviceId={}, httpStatus={}", deviceId, e.status(), e))
				.onErrorResume(FeignException.class, e -> e.status() == 404
						? Mono.error(new DeviceNotFoundException(deviceId))
						: Mono.error(e)));
	}

	@Override
	public Mono<EventView> getEventById(String eventId, String deviceId) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx,
						() -> client.getEventById(eventId, deviceId)))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientEventOutboundAdapter::toEventView)
				.doOnSubscribe(s -> log.debug("Outbound event-service getEvent eventId={}, deviceId={}", eventId, deviceId))
				.doOnError(FeignException.class, e -> {
					if (e.status() == 404) {
						log.debug("Outbound event-service event not found eventId={}, deviceId={}", eventId, deviceId);
					} else {
						log.warn("Outbound event-service getEvent failed eventId={}, deviceId={}, httpStatus={}",
								eventId, deviceId, e.status(), e);
					}
				})
				.onErrorResume(FeignException.class, e -> e.status() == 404 ? Mono.empty() : Mono.error(e)));
	}

	private static <T> T feignWithBearer(ContextView ctx, Supplier<T> supplier) {
		String token = ctx.getOrDefault(ReactiveBearerContext.KEY, "");
		try {
			if (!token.isBlank()) {
				ServiceJwtHolder.setBearerToken(token);
			}
			return supplier.get();
		} finally {
			ServiceJwtHolder.clear();
		}
	}

	private static EventPageResult toEventPageResult(EventPage eventPage) {
		List<EventView> events = eventPage.getEvents() == null
				? List.of()
				: eventPage.getEvents().stream().map(ResilientEventOutboundAdapter::toEventView).toList();
		return new EventPageResult(events, eventPage.getPage(), eventPage.getSize(), eventPage.getTotal());
	}

	private static EventView toEventView(Event event) {
		return new EventView(
				event.getEventId(),
				event.getDeviceId(),
				event.getTimestamp(),
				event.getType(),
				event.getPayload());
	}
}
