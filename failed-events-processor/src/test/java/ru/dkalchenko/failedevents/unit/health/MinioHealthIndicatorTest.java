package ru.dkalchenko.failedevents.unit.health;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import ru.dkalchenko.failedevents.health.MinioHealthIndicator;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioHealthIndicatorTest {

	@Mock
	MinioClient minioClient;

	@Mock
	FailedEventsProcessorProperties properties;

	@InjectMocks
	MinioHealthIndicator minioHealthIndicator;

	FailedEventsProcessorProperties.Minio minio;

	@BeforeEach
	void setUp() {
		minio = new FailedEventsProcessorProperties.Minio();
		minio.setBucketFailedEvents("failed-events");
		minio.setBucketFailedDevices("failed-devices");
	}

	@Test
	void healthReturnsUpWhenBothBucketsExist() throws Exception {
		when(properties.getMinio()).thenReturn(minio);

		Health health = minioHealthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails())
				.containsEntry("bucketFailedEvents", "failed-events")
				.containsEntry("bucketFailedDevices", "failed-devices");

		verify(minioClient).bucketExists(argThat(args ->
				"failed-events".equals(args.bucket())));
		verify(minioClient).bucketExists(argThat(args ->
				"failed-devices".equals(args.bucket())));
	}

	@Test
	void healthReturnsDownWhenFirstBucketCheckThrowsException() throws Exception {
		when(properties.getMinio()).thenReturn(minio);
		doThrow(new RuntimeException("MinIO unavailable"))
				.when(minioClient).bucketExists(argThat(args -> "failed-events".equals(args.bucket())));

		Health health = minioHealthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).isNotEmpty();
		assertThat(health.getDetails().get("error")).isNotNull()
				.asString().contains("MinIO unavailable");
	}

	@Test
	void healthReturnsDownWhenSecondBucketCheckThrowsException() throws Exception {
		when(properties.getMinio()).thenReturn(minio);
		doAnswer(invocation -> {
			BucketExistsArgs args = invocation.getArgument(0);
			if ("failed-devices".equals(args.bucket())) {
				throw new RuntimeException("Bucket not found");
			}
			return true;
		}).when(minioClient).bucketExists(any(BucketExistsArgs.class));

		Health health = minioHealthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).isNotEmpty();
		assertThat(health.getDetails().get("error")).isNotNull()
				.asString().contains("Bucket not found");
	}

	@Test
	void healthReturnsDownWhenGetMinioReturnsNull() {
		when(properties.getMinio()).thenReturn(null);

		Health health = minioHealthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsKey("error");
	}
}
