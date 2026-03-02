package ru.dkalchenko.failedevents.health;

import io.minio.MinioClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;

/**
 * Health indicator для MinIO: проверяет доступность хранилища и бакетов.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioHealthIndicator implements HealthIndicator {

	MinioClient minioClient;
	FailedEventsProcessorProperties properties;

	@Override
	public Health health() {
		try {
			String bucketEvents = properties.getMinio().getBucketFailedEvents();
			String bucketDevices = properties.getMinio().getBucketFailedDevices();
			minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketEvents).build());
			minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketDevices).build());
			return Health.up()
					.withDetail("bucketFailedEvents", bucketEvents)
					.withDetail("bucketFailedDevices", bucketDevices)
					.build();
		} catch (Exception e) {
			return Health.down()
					.withException(e)
					.build();
		}
	}
}
