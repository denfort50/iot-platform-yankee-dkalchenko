package ru.dkalchenko.failedevents.properties;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурационные свойства для failed-events-processor.
 */
@Data
@ConfigurationProperties(prefix = "failed-events-processor")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FailedEventsProcessorProperties {

	Listener listener = new Listener();
	Minio minio = new Minio();

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	public static class Listener {
		String dltTopic = "dlt.v1";
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	public static class Minio {
		String endpoint;
		String accessKey;
		String secretKey;
		String bucketFailedEvents = "failed-events";
		String bucketFailedDevices = "failed-devices";
	}
}
