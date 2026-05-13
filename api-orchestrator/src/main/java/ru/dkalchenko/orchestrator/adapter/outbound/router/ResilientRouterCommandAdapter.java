package ru.dkalchenko.orchestrator.adapter.outbound.router;

import api.v1.RouterManagement;
import api.v1.RouterManagementServiceGrpc;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.dkalchenko.orchestrator.application.model.RouterCommandMessage;
import ru.dkalchenko.orchestrator.application.exception.RouterNotFoundException;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;
import ru.dkalchenko.orchestrator.domain.port.RouterCommandPort;

import java.util.List;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResilientRouterCommandAdapter implements RouterCommandPort {

	static String NAME = "routerManagement";
	private static final Metadata.Key<String> AUTHORIZATION_HEADER =
			Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

	RouterManagementServiceGrpc.RouterManagementServiceBlockingStub stub;
	CircuitBreaker circuitBreaker;
	TimeLimiter timeLimiter;

	public ResilientRouterCommandAdapter(RouterManagementServiceGrpc.RouterManagementServiceBlockingStub stub,
			CircuitBreakerRegistry cbRegistry,
			TimeLimiterRegistry tlRegistry) {
		this.stub = stub;
		this.circuitBreaker = cbRegistry.circuitBreaker(NAME);
		this.timeLimiter = tlRegistry.timeLimiter(NAME);
	}

	@Override
	public Mono<List<String>> sendDeviceFirmwareUpdate(String routerId, String deviceId, String targetVersion) {
		String payload = "{\"deviceId\":\"" + escapeJson(deviceId) + "\",\"targetVersion\":\""
				+ escapeJson(targetVersion) + "\"}";
		return sendCommand(routerId, "DEVICE_FIRMWARE_UPDATE", payload);
	}

	@Override
	public Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson) {
		return sendCommand(routerId, commandType, payloadJson, null);
	}

	@Override
	public Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson, String bearerToken) {
		return withResolvedToken(bearerToken, forStub -> Mono.fromCallable(() -> {
			log.debug("Outbound router-management sendCommand routerId={}, commandType={}, payloadChars={}",
					routerId, commandType, payloadJson != null ? payloadJson.length() : 0);
			RouterManagementServiceGrpc.RouterManagementServiceBlockingStub callStub = withBearerToken(stub, forStub);
			RouterManagement.SendCommandRequest.Builder request = RouterManagement.SendCommandRequest.newBuilder()
					.setCommandType(commandType)
					.setPayload(payloadJson != null ? payloadJson : "");
			if (routerId != null && !routerId.isBlank()) {
				request.setRouterId(routerId);
			}
			RouterManagement.SendCommandResponse response = callStub.sendCommand(request.build());
			if (response.hasError()) {
				String error = response.getError();
				if (isInvalidRouterIdError(error)) {
					throw new IllegalArgumentException("routerId must be a valid UUID");
				}
				if (isRouterNotFoundError(error)) {
					throw new RouterNotFoundException(routerId != null ? routerId : "");
				}
				throw new IllegalStateException(error);
			}
			log.debug("Outbound router-management sendCommand ok routerId={}, commandIdsCount={}",
					routerId, response.getCommandIdsCount());
			return List.copyOf(response.getCommandIdsList());
		}).subscribeOn(Schedulers.boundedElastic()))
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.doOnError(e -> log.warn("Outbound router-management sendCommand failed routerId={}, commandType={}: {}",
						routerId, commandType, e.toString()));
	}

	@Override
	public Mono<String> registerRouter(String inventoryNumber, String name, String model, String location, String bearerToken) {
		return withResolvedToken(bearerToken, forStub -> Mono.fromCallable(() -> {
			log.debug("Outbound router-management registerRouter inventoryNumber={}, name={}", inventoryNumber, name);
			RouterManagementServiceGrpc.RouterManagementServiceBlockingStub callStub = withBearerToken(stub, forStub);
			RouterManagement.RegisterRouterResponse response = callStub.registerRouter(
					RouterManagement.RegisterRouterRequest.newBuilder()
							.setInventoryNumber(inventoryNumber != null ? inventoryNumber : "")
							.setName(name != null ? name : "")
							.setModel(model != null ? model : "")
							.setLocation(location != null ? location : "")
							.build());
			if (response.hasError()) {
				throw toDomainException(response.getError(), null);
			}
			log.debug("Outbound router-management registerRouter ok routerId={}", response.getRouterId());
			return response.getRouterId();
		}).subscribeOn(Schedulers.boundedElastic()))
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.doOnError(e -> log.warn("Outbound router-management registerRouter failed inventoryNumber={}, name={}: {}",
						inventoryNumber, name, e.toString()));
	}

	@Override
	public Mono<List<RouterCommandMessage>> pollCommands(String routerId, String bearerToken) {
		return withResolvedToken(bearerToken, forStub -> Mono.fromCallable(() -> {
			log.debug("Outbound router-management pollCommands routerId={}", routerId);
			RouterManagementServiceGrpc.RouterManagementServiceBlockingStub callStub = withBearerToken(stub, forStub);
			RouterManagement.CommandsPollResponse response = callStub.commandsPoll(
					RouterManagement.CommandsPollRequest.newBuilder()
							.setRouterId(routerId != null ? routerId : "")
							.build());
			if (response.hasError()) {
				throw toDomainException(response.getError(), routerId);
			}
			List<RouterCommandMessage> commands = response.getCommandsList().stream()
					.map(c -> new RouterCommandMessage(c.getCommandId(), c.getCommandType(), c.getPayload(), c.getStatus()))
					.toList();
			log.debug("Outbound router-management pollCommands ok routerId={}, commandsCount={}", routerId, commands.size());
			return commands;
		}).subscribeOn(Schedulers.boundedElastic()))
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.doOnError(e -> log.warn("Outbound router-management pollCommands failed routerId={}: {}", routerId, e.toString()));
	}

	@Override
	public Mono<Boolean> ackCommand(String routerId, String commandId, String bearerToken) {
		return withResolvedToken(bearerToken, forStub -> Mono.fromCallable(() -> {
			log.debug("Outbound router-management ackCommand routerId={}, commandId={}", routerId, commandId);
			RouterManagementServiceGrpc.RouterManagementServiceBlockingStub callStub = withBearerToken(stub, forStub);
			RouterManagement.CommandsAckResponse response = callStub.commandsAck(
					RouterManagement.CommandsAckRequest.newBuilder()
							.setRouterId(routerId != null ? routerId : "")
							.setCommandId(commandId != null ? commandId : "")
							.build());
			if (response.hasError()) {
				throw toDomainException(response.getError(), routerId);
			}
			log.debug("Outbound router-management ackCommand ok routerId={}, commandId={}, success={}",
					routerId, commandId, response.getSuccess());
			return response.getSuccess();
		}).subscribeOn(Schedulers.boundedElastic()))
				.transformDeferred(TimeLimiterOperator.of(timeLimiter))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.doOnError(e -> log.warn("Outbound router-management ackCommand failed routerId={}, commandId={}: {}",
						routerId, commandId, e.toString()));
	}

	private static RuntimeException toDomainException(String error, String routerId) {
		if (isInvalidRouterIdError(error) || isInvalidCommandIdError(error)) {
			return new IllegalArgumentException(error);
		}
		if (isRouterNotFoundError(error)) {
			return new RouterNotFoundException(routerId != null ? routerId : "");
		}
		return new IllegalStateException(error);
	}

	private static <T> Mono<T> withResolvedToken(String bearerToken, java.util.function.Function<String, Mono<T>> action) {
		return Mono.deferContextual(ctxView -> {
			String fromContext = ctxView.getOrDefault(ReactiveBearerContext.KEY, "");
			String effective = bearerToken != null && !bearerToken.isBlank() ? bearerToken : fromContext;
			String forStub = effective.isBlank() ? null : effective;
			return action.apply(forStub);
		});
	}

	private static RouterManagementServiceGrpc.RouterManagementServiceBlockingStub withBearerToken(
			RouterManagementServiceGrpc.RouterManagementServiceBlockingStub baseStub,
			String bearerToken) {
		if (bearerToken == null || bearerToken.isBlank()) {
			return baseStub;
		}
		Metadata metadata = new Metadata();
		metadata.put(AUTHORIZATION_HEADER, "Bearer " + bearerToken);
		return baseStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
	}

	private static boolean isInvalidRouterIdError(String error) {
		return error != null && error.toLowerCase().contains("invalid router_id");
	}

	private static boolean isRouterNotFoundError(String error) {
		return error != null && error.toLowerCase().contains("router not found");
	}

	private static boolean isInvalidCommandIdError(String error) {
		return error != null && error.toLowerCase().contains("invalid command_id");
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
