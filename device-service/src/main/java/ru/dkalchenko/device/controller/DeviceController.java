package ru.dkalchenko.device.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.dkalchenko.device.api.model.CreateResponse;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.api.model.MessageResponse;
import ru.dkalchenko.device.api.model.TargetVersionRequest;
import ru.dkalchenko.device.api.server.DevicesApiDelegate;
import ru.dkalchenko.device.service.DeviceService;

/**
 * Реализация контракта API устройств (OpenAPI): делегирует вызовы в {@link DeviceService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceController implements DevicesApiDelegate {

	DeviceService deviceService;

	@Override
	public ResponseEntity<CreateResponse> createDevice(DeviceRequest deviceRequest) {
		String deviceId = deviceService.createDevice(deviceRequest);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new CreateResponse()
						.message("Device create request accepted")
						.deviceId(deviceId));
	}

	@Override
	public ResponseEntity<Device> getDevice(String deviceId) {
		return deviceService.getDevice(deviceId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@Override
	public ResponseEntity<MessageResponse> setDeviceTargetVersion(String deviceId, TargetVersionRequest targetVersionRequest) {
		return deviceService.setDeviceTargetVersion(deviceId, targetVersionRequest);
	}

	@Override
	public ResponseEntity<MessageResponse> updateDevice(String deviceId, DeviceRequest deviceRequest) {
		deviceService.updateDevice(deviceId, deviceRequest);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new MessageResponse()
						.message("Device update request accepted")
						.deviceId(deviceId));
	}

	@Override
	public ResponseEntity<MessageResponse> deleteDevice(String deviceId) {
		deviceService.deleteDevice(deviceId);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new MessageResponse()
						.message("Device delete request accepted")
						.deviceId(deviceId));
	}
}
