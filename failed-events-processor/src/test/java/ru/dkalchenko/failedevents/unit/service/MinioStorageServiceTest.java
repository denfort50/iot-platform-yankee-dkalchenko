package ru.dkalchenko.failedevents.unit.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.failedevents.model.FailedDeviceJson;
import ru.dkalchenko.failedevents.model.FailedEventJson;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;
import ru.dkalchenko.failedevents.service.MinioStorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

	@Mock
	MinioClient minioClient;
	@Mock
	FailedEventsProcessorProperties properties;

	@InjectMocks
	MinioStorageService minioStorageService;

	@Test
	void saveFailedEventUploadsToFailedEventsBucket() throws Exception {
		FailedEventsProcessorProperties.Minio minio = new FailedEventsProcessorProperties.Minio();
		minio.setBucketFailedEvents("failed-events");
		minio.setBucketFailedDevices("failed-devices");
		when(properties.getMinio()).thenReturn(minio);

		FailedEventJson payload = FailedEventJson.builder()
				.failedEvent(FailedEventJson.FailedEvent.builder().eventId("e1").build())
				.receivedAt(1L)
				.build();

		String objectName = minioStorageService.saveFailedEvent(payload, "eventType");

		assertThat(objectName)
				.startsWith("eventType_")
				.endsWith(".json");

		verify(minioClient).putObject(any(PutObjectArgs.class));
	}

	@Test
	void saveFailedDeviceUploadsToFailedDevicesBucket() throws Exception {
		FailedEventsProcessorProperties.Minio minio = new FailedEventsProcessorProperties.Minio();
		minio.setBucketFailedEvents("failed-events");
		minio.setBucketFailedDevices("failed-devices");
		when(properties.getMinio()).thenReturn(minio);

		FailedDeviceJson payload = FailedDeviceJson.builder()
				.failedDevice(FailedDeviceJson.FailedDevice.builder().deviceId("d1").build())
				.receivedAt(1L)
				.build();

		String objectName = minioStorageService.saveFailedDevice(payload, "CREATE");

		assertThat(objectName)
				.startsWith("CREATE_")
				.endsWith(".json");

		verify(minioClient).putObject(any(PutObjectArgs.class));
	}
}

