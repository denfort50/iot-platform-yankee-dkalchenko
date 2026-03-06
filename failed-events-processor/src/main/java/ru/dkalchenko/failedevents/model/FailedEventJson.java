package ru.dkalchenko.failedevents.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * JSON-структура для сохранения в MinIO (бакет failed-events).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FailedEventJson {

	FailedEvent failedEvent;
	ErrorMeta errorMeta;
	long receivedAt;

	@Value
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FailedEvent {
		String eventId;
		String deviceId;
		String type;
		long timestamp;
		String payload;
	}

	@Value
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ErrorMeta {
		String errorSource;
		String reason;
		String stackTrace;
	}
}
