package ru.dkalchenko.orchestrator.domain.port;

import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.CreateDeviceResult;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.model.TargetVersionCommand;

public interface DeviceOutboundPort {

	Mono<DeviceView> getDevice(String deviceId);

	Mono<CreateDeviceResult> createDevice(DeviceCommand request);

	Mono<MessageResult> updateDevice(String deviceId, DeviceCommand request);

	Mono<MessageResult> deleteDevice(String deviceId);

	Mono<MessageResult> setDeviceTargetVersion(String deviceId, TargetVersionCommand request);
}
