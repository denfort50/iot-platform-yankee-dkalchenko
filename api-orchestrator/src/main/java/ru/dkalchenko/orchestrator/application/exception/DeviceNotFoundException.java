package ru.dkalchenko.orchestrator.application.exception;

public class DeviceNotFoundException extends RuntimeException {

	public DeviceNotFoundException(String deviceId) {
		super("Device not found: " + deviceId);
	}
}
