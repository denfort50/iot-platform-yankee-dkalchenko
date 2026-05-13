package ru.dkalchenko.orchestrator.adapter.inbound.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.adapter.inbound.api.generated.DevicesApiDelegate;
import ru.dkalchenko.orchestrator.api.model.CreateResponse;
import ru.dkalchenko.orchestrator.api.model.Device;
import ru.dkalchenko.orchestrator.api.model.DeviceRequest;
import ru.dkalchenko.orchestrator.api.model.DeviceVersionUpdateRequest;
import ru.dkalchenko.orchestrator.api.model.MessageResponse;
import ru.dkalchenko.orchestrator.api.model.VersionUpdateResult;
import ru.dkalchenko.orchestrator.application.model.CreateDeviceResult;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.port.in.DeviceUseCase;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceProxyController implements DevicesApiDelegate {

	DeviceUseCase deviceUseCase;

	@Override
	public Mono<ResponseEntity<CreateResponse>> createDevice(Mono<DeviceRequest> deviceRequest, ServerWebExchange exchange) {
		return deviceRequest
				.doOnNext(r -> log.debug("createDevice request deviceType={}", r.getDeviceType()))
				.flatMap(request -> deviceUseCase.createDevice(toDeviceCommand(request)))
				.doOnNext(result -> log.debug("createDevice accepted deviceId={}", result.deviceId()))
				.map(DeviceProxyController::toCreateResponse)
				.map(body -> ResponseEntity.status(HttpStatus.ACCEPTED).body(body));
	}

	@Override
	public Mono<ResponseEntity<Device>> getDevice(String deviceId, ServerWebExchange exchange) {
		return deviceUseCase.getDevice(deviceId)
				.doOnSubscribe(s -> log.debug("getDevice deviceId={}", deviceId))
				.map(DeviceProxyController::toApiDevice)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@Override
	public Mono<ResponseEntity<MessageResponse>> updateDevice(String deviceId, Mono<DeviceRequest> deviceRequest, ServerWebExchange exchange) {
		return deviceRequest
				.doOnNext(r -> log.debug("updateDevice request deviceId={}, deviceType={}", deviceId, r.getDeviceType()))
				.flatMap(request -> deviceUseCase.updateDevice(deviceId, toDeviceCommand(request)))
				.map(DeviceProxyController::toMessageResponse)
				.map(body -> ResponseEntity.status(HttpStatus.ACCEPTED).body(body));
	}

	@Override
	public Mono<ResponseEntity<MessageResponse>> deleteDevice(String deviceId, ServerWebExchange exchange) {
		return deviceUseCase.deleteDevice(deviceId)
				.doOnSubscribe(s -> log.debug("deleteDevice deviceId={}", deviceId))
				.map(DeviceProxyController::toMessageResponse)
				.map(body -> ResponseEntity.status(HttpStatus.ACCEPTED).body(body));
	}

	@Override
	public Mono<ResponseEntity<VersionUpdateResult>> updateTargetVersion(String deviceId,
			Mono<DeviceVersionUpdateRequest> deviceVersionUpdateRequest,
			ServerWebExchange exchange) {
		return deviceVersionUpdateRequest
				.doOnNext(body -> log.info("updateTargetVersion request deviceId={}, routerId={}, targetVersion={}", deviceId,
						body.getRouterId(), body.getTargetVersion()))
				.flatMap(body -> deviceUseCase.updateDeviceVersion(deviceId, toVersionUpdateRequest(body)))
				.map(DeviceProxyController::toApiVersionUpdateResult)
				.map(result -> Boolean.TRUE.equals(result.getCompensated())
						? ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result)
						: ResponseEntity.ok(result));
	}

	private static DeviceCommand toDeviceCommand(DeviceRequest request) {
		return new DeviceCommand(request.getDeviceType(), request.getMeta());
	}

	private static ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest toVersionUpdateRequest(
			DeviceVersionUpdateRequest request) {
		return new ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest(
				request.getTargetVersion(),
				request.getRouterId(),
				request.getIdempotencyKey());
	}

	private static CreateResponse toCreateResponse(CreateDeviceResult result) {
		return new CreateResponse().message(result.message()).deviceId(result.deviceId());
	}

	private static MessageResponse toMessageResponse(MessageResult result) {
		return new MessageResponse().message(result.message()).deviceId(result.deviceId());
	}

	private static Device toApiDevice(DeviceView view) {
		return new Device()
				.deviceId(view.deviceId())
				.deviceType(view.deviceType())
				.createdAt(view.createdAt())
				.meta(view.meta())
				.updatedAt(view.updatedAt())
				.targetSoftwareVersion(view.targetSoftwareVersion());
	}

	private static VersionUpdateResult toApiVersionUpdateResult(ru.dkalchenko.orchestrator.application.model.VersionUpdateResult result) {
		return new VersionUpdateResult()
				.compensated(result.compensated())
				.message(result.message())
				.createdCommandIds(result.createdCommandIds());
	}
}
