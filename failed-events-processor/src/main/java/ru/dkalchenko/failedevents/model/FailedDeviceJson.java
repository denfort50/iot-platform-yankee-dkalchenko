package ru.dkalchenko.failedevents.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * JSON-структура для сохранения в MinIO (бакет failed-devices).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FailedDeviceJson {

	FailedDevice failedDevice;
	FailedEventJson.ErrorMeta errorMeta;
	long receivedAt;

	@Value
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FailedDevice {
		String deviceId;
		String deviceType;
		Long createdAt;
		String meta;
	}
}
