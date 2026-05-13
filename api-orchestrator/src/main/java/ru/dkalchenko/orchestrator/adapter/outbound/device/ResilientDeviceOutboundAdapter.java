package ru.dkalchenko.orchestrator.adapter.outbound.device;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.ContextView;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;
import ru.dkalchenko.orchestrator.config.ServiceJwtHolder;
import ru.dkalchenko.device.api.model.TargetVersionRequest;
import ru.dkalchenko.orchestrator.adapter.outbound.feign.DeviceServiceFeignClient;
import ru.dkalchenko.orchestrator.application.exception.UpstreamConflictException;
import ru.dkalchenko.orchestrator.application.model.CreateDeviceResult;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.model.TargetVersionCommand;
import ru.dkalchenko.device.api.model.CreateResponse;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.api.model.MessageResponse;
import ru.dkalchenko.orchestrator.domain.port.DeviceOutboundPort;

import java.util.function.Supplier;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResilientDeviceOutboundAdapter implements DeviceOutboundPort {

	static String NAME = "deviceService";

	DeviceServiceFeignClient client;
	CircuitBreaker circuitBreaker;
	TimeLimiter timeLimiter;

	public ResilientDeviceOutboundAdapter(DeviceServiceFeignClient client,
			CircuitBreakerRegistry cbRegistry,
			TimeLimiterRegistry tlRegistry) {
		this.client = client;
		this.circuitBreaker = cbRegistry.circuitBreaker(NAME);
		this.timeLimiter = tlRegistry.timeLimiter(NAME);
	}

	@Override
	public Mono<DeviceView> getDevice(String deviceId) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx, () -> client.getDevice(deviceId)))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientDeviceOutboundAdapter::toDeviceView)
				.doOnSubscribe(s -> log.debug("Outbound device-service GET deviceId={}", deviceId))
				.doOnError(FeignException.class, e -> {
					if (e.status() == 404) {
						log.debug("Outbound device-service device not found deviceId={}", deviceId);
					} else {
						log.warn("Outbound device-service GET failed deviceId={}, httpStatus={}", deviceId, e.status(), e);
					}
				})
				.onErrorResume(FeignException.class, e -> e.status() == 404 ? Mono.empty() : Mono.error(e)));
	}

	@Override
	public Mono<CreateDeviceResult> createDevice(DeviceCommand request) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx, () -> client.createDevice(toDeviceRequest(request))))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientDeviceOutboundAdapter::toCreateDeviceResult)
				.doOnSubscribe(s -> log.debug("Outbound device-service POST create deviceType={}", request.deviceType()))
				.doOnError(FeignException.class,
						e -> log.warn("Outbound device-service POST create failed deviceType={}, httpStatus={}",
								request.deviceType(), e.status(), e)));
	}

	@Override
	public Mono<MessageResult> updateDevice(String deviceId, DeviceCommand request) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx, () -> client.updateDevice(deviceId, toDeviceRequest(request))))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientDeviceOutboundAdapter::toMessageResult)
				.doOnSubscribe(s -> log.debug("Outbound device-service PUT deviceId={}", deviceId))
				.doOnError(FeignException.class,
						e -> log.warn("Outbound device-service PUT failed deviceId={}, httpStatus={}", deviceId, e.status(), e)));
	}

	@Override
	public Mono<MessageResult> deleteDevice(String deviceId) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx, () -> client.deleteDevice(deviceId)))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientDeviceOutboundAdapter::toMessageResult)
				.doOnSubscribe(s -> log.debug("Outbound device-service DELETE deviceId={}", deviceId))
				.doOnError(FeignException.class,
						e -> log.warn("Outbound device-service DELETE failed deviceId={}, httpStatus={}", deviceId, e.status(), e)));
	}

	@Override
	public Mono<MessageResult> setDeviceTargetVersion(String deviceId, TargetVersionCommand request) {
		return Mono.deferContextual(ctx -> Mono.fromCallable(() -> feignWithBearer(ctx,
				() -> client.setDeviceTargetVersion(deviceId, toTargetVersionRequest(request))))
				.subscribeOn(Schedulers.boundedElastic())
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.map(ResilientDeviceOutboundAdapter::toMessageResult)
				.doOnSubscribe(s -> log.debug("Outbound device-service PATCH targetVersion deviceId={}, targetVersion={}",
						deviceId, request.targetVersion()))
				.doOnError(FeignException.class,
						e -> log.warn("Outbound device-service PATCH targetVersion failed deviceId={}, httpStatus={}",
								deviceId, e.status(), e))
				.onErrorMap(FeignException.class, ResilientDeviceOutboundAdapter::mapFeignException));
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

	private static Throwable mapFeignException(FeignException ex) {
		if (ex.status() == 409) {
			return new UpstreamConflictException(ex.getMessage());
		}
		return ex;
	}

	private static DeviceRequest toDeviceRequest(DeviceCommand request) {
		return new DeviceRequest()
				.deviceType(request.deviceType())
				.meta(request.meta());
	}

	private static TargetVersionRequest toTargetVersionRequest(TargetVersionCommand request) {
		return new TargetVersionRequest()
				.targetVersion(request.targetVersion())
				.idempotencyKey(request.idempotencyKey());
	}

	private static CreateDeviceResult toCreateDeviceResult(CreateResponse response) {
		return new CreateDeviceResult(response.getMessage(), response.getDeviceId());
	}

	private static MessageResult toMessageResult(MessageResponse response) {
		return new MessageResult(response.getMessage(), response.getDeviceId());
	}

	private static DeviceView toDeviceView(Device device) {
		return new DeviceView(
				device.getDeviceId(),
				device.getDeviceType(),
				device.getCreatedAt(),
				device.getMeta(),
				device.getUpdatedAt(),
				device.getTargetSoftwareVersion());
	}
}
