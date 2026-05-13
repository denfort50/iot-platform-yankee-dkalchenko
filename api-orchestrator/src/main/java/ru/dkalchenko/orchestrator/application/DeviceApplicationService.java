package ru.dkalchenko.orchestrator.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.CreateDeviceResult;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;
import ru.dkalchenko.orchestrator.application.port.in.DeviceUseCase;
import ru.dkalchenko.orchestrator.domain.port.DeviceOutboundPort;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceApplicationService implements DeviceUseCase {

	DeviceOutboundPort devices;
	VersionUpdateSagaService versionUpdateSagaService;

	@Override
	public Mono<CreateDeviceResult> createDevice(DeviceCommand request) {
		return devices.createDevice(request)
				.doOnSubscribe(s -> log.debug("createDevice service deviceType={}", request.deviceType()));
	}

	@Override
	public Mono<DeviceView> getDevice(String deviceId) {
		return devices.getDevice(deviceId)
				.doOnSubscribe(s -> log.debug("getDevice service deviceId={}", deviceId));
	}

	@Override
	public Mono<MessageResult> updateDevice(String deviceId, DeviceCommand request) {
		return devices.updateDevice(deviceId, request)
				.doOnSubscribe(s -> log.debug("updateDevice service deviceId={}", deviceId));
	}

	@Override
	public Mono<MessageResult> deleteDevice(String deviceId) {
		return devices.deleteDevice(deviceId)
				.doOnSubscribe(s -> log.debug("deleteDevice service deviceId={}", deviceId));
	}

	@Override
	public Mono<VersionUpdateResult> updateDeviceVersion(String deviceId, DeviceVersionUpdateRequest request) {
		return versionUpdateSagaService.updateDeviceVersion(deviceId, request)
				.doOnSubscribe(s -> log.debug("updateDeviceVersion service deviceId={}, targetVersion={}",
						deviceId, request.targetVersion()));
	}
}
