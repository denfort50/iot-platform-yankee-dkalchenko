package ru.dkalchenko.failedevents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dkalchenko.failedevents.model.FailedDeviceJson;
import ru.dkalchenko.failedevents.model.FailedEventJson;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Сохраняет JSON-объекты в MinIO (бакеты failed-events и failed-devices).
 * Имя файла: {eventType}_{timestamp}_{uuid}.json
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioStorageService {

	MinioClient minioClient;
	FailedEventsProcessorProperties properties;
	ObjectMapper objectMapper = new ObjectMapper();

	public String saveFailedEvent(FailedEventJson json, String eventType) {
		String objectName = buildObjectName(eventType);
		String bucket = properties.getMinio().getBucketFailedEvents();
		return uploadJson(bucket, objectName, json);
	}

	public String saveFailedDevice(FailedDeviceJson json, String eventType) {
		String objectName = buildObjectName(eventType);
		String bucket = properties.getMinio().getBucketFailedDevices();
		return uploadJson(bucket, objectName, json);
	}

	private String buildObjectName(String eventType) {
		long ts = System.currentTimeMillis();
		String uuid = UUID.randomUUID().toString();
		return eventType + "_" + ts + "_" + uuid + ".json";
	}

	private String uploadJson(String bucket, String objectName, Object payload) {
		try {
			String json = objectMapper.writeValueAsString(payload);
			byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
			try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
				minioClient.putObject(
						PutObjectArgs.builder()
								.bucket(bucket)
								.object(objectName)
								.stream(in, bytes.length, -1)
								.contentType("application/json")
								.build()
				);
			}
			log.debug("Saved to MinIO: bucket={}, object={}", bucket, objectName);
			return objectName;
		} catch (Exception e) {
			throw new RuntimeException("Failed to upload to MinIO: " + bucket + "/" + objectName, e);
		}
	}
}
