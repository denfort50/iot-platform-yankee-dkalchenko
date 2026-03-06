package ru.dkalchenko.failedevents.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;

@Slf4j
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioConfig {

	FailedEventsProcessorProperties properties;

	@Bean
	@ConditionalOnProperty(name = "failed-events-processor.minio.endpoint")
	public MinioClient minioClient() {
		FailedEventsProcessorProperties.Minio minio = properties.getMinio();
		MinioClient client = MinioClient.builder()
				.endpoint(minio.getEndpoint())
				.credentials(minio.getAccessKey(), minio.getSecretKey())
				.build();
		ensureBuckets(client, minio.getBucketFailedEvents(), minio.getBucketFailedDevices());
		return client;
	}

	private void ensureBuckets(MinioClient client, String bucketEvents, String bucketDevices) {
		try {
			if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketEvents).build())) {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucketEvents).build());
				log.info("Created MinIO bucket: {}", bucketEvents);
			}
			if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketDevices).build())) {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucketDevices).build());
				log.info("Created MinIO bucket: {}", bucketDevices);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to ensure MinIO buckets", e);
		}
	}
}
