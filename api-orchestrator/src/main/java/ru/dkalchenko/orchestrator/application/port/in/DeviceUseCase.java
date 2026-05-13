package ru.dkalchenko.orchestrator.application.port.in;

import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.CreateDeviceResult;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;

public interface DeviceUseCase {

	Mono<CreateDeviceResult> createDevice(DeviceCommand request);

	Mono<DeviceView> getDevice(String deviceId);

	Mono<MessageResult> updateDevice(String deviceId, DeviceCommand request);

	Mono<MessageResult> deleteDevice(String deviceId);

	Mono<VersionUpdateResult> updateDeviceVersion(String deviceId, DeviceVersionUpdateRequest request);
}
