package ru.dkalchenko.device.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.service.DeviceService;

import java.util.Map;

/**
 * REST API для управления устройствами.
 */
@Slf4j
@RestController
@RequestMapping("/v1/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceController {

	DeviceService deviceService;

	@PostMapping
	public ResponseEntity<Map<String, String>> createDevice(@Valid @RequestBody DeviceRequest request) {
		String deviceId = deviceService.createDevice(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(Map.of("message", "Device create request accepted", "deviceId", deviceId));
	}

	@GetMapping("/{deviceId}")
	public ResponseEntity<Device> getDevice(@PathVariable String deviceId) {
		return deviceService.getDevice(deviceId)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{deviceId}")
	public ResponseEntity<Map<String, String>> updateDevice(
			@PathVariable String deviceId,
			@Valid @RequestBody DeviceRequest request) {
		deviceService.updateDevice(deviceId, request);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(Map.of("message", "Device update request accepted", "deviceId", deviceId));
	}

	@DeleteMapping("/{deviceId}")
	public ResponseEntity<Map<String, String>> deleteDevice(@PathVariable String deviceId) {
		deviceService.deleteDevice(deviceId);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(Map.of("message", "Device delete request accepted", "deviceId", deviceId));
	}
}
