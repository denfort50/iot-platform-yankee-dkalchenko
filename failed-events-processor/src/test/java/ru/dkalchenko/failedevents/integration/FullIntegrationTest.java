package ru.dkalchenko.failedevents.integration;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import ru.dkalchenko.failedevents.properties.FailedEventsProcessorProperties;
import ru.dkalchenko.failedevents.testcontainer.base.AbstractIntegrationTest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Полный интеграционный тест: отправка DeadLetterMessage в Kafka dlt.v1 → DltListener потребляет →
 * парсит originalValue → сохраняет JSON в MinIO. Проверяется весь путь через реальный Kafka и MinIO.
 */
class FullIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	KafkaTemplate<String, GenericRecord> kafkaAvroProducerTemplate;
	@Autowired
	MinioClient minioClient;
	@Autowired
	FailedEventsProcessorProperties properties;

	@Test
	void whenProduceDeviceEventToDltThenListenerSavesToMinioFailedEventsBucket() throws Exception {
		String originalValueJson = """
				{"eventId":"e-int-1","deviceId":"device-123","type":"TEMPERATURE","timestamp":1718893844555,"payload":"{\\"value\\":25}"}
				""".trim();
		GenericRecord dltMessage = buildDeadLetterMessage(originalValueJson, "events-collector-service", "DB write timeout");

		String key = "test-key-events";
		kafkaAvroProducerTemplate.send(getDltTopic(), key, dltMessage).get(10, TimeUnit.SECONDS);

		String bucket = properties.getMinio().getBucketFailedEvents();

		String objectName = await()
				.atMost(30, TimeUnit.SECONDS)
				.pollInterval(500, TimeUnit.MILLISECONDS)
				.until(() -> findFirstObjectInBucket(bucket, "TEMPERATURE_"), Objects::nonNull);

		assertThat(objectName).isNotNull();

		try (InputStream in = minioClient.getObject(
				GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);
			String json = out.toString(StandardCharsets.UTF_8);
			assertThat(json).contains("\"eventId\":\"e-int-1\"");
			assertThat(json).contains("\"deviceId\":\"device-123\"");
			assertThat(json).contains("\"errorSource\":\"events-collector-service\"");
			assertThat(json).contains("\"reason\":\"DB write timeout\"");
		}
	}

	@Test
	void whenProduceDeviceInfoToDltThenListenerSavesToMinioFailedDevicesBucket() throws Exception {
		String originalValueJson = """
				{"operation":"CREATE","deviceId":"device-456","deviceType":"sensor","createdAt":1718893844555,"meta":"{\\"location\\":\\"room1\\"}"}
				""".trim();
		GenericRecord dltMessage = buildDeadLetterMessage(originalValueJson, "device-collector-service", "Insert failed");

		String key = "test-key-devices";
		kafkaAvroProducerTemplate.send(getDltTopic(), key, dltMessage).get(10, TimeUnit.SECONDS);

		String bucket = properties.getMinio().getBucketFailedDevices();

		String objectName = await()
				.atMost(30, TimeUnit.SECONDS)
				.pollInterval(500, TimeUnit.MILLISECONDS)
				.until(() -> findFirstObjectInBucket(bucket, "CREATE_"), Objects::nonNull);

		assertThat(objectName).isNotNull();

		try (InputStream in = minioClient.getObject(
				GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);
			String json = out.toString(StandardCharsets.UTF_8);
			assertThat(json).contains("\"deviceId\":\"device-456\"");
			assertThat(json).contains("\"deviceType\":\"sensor\"");
			assertThat(json).contains("\"errorSource\":\"device-collector-service\"");
			assertThat(json).contains("\"reason\":\"Insert failed\"");
		}
	}

	private String findFirstObjectInBucket(String bucket, String prefix) {
		try {
			Iterable<Result<Item>> objects = minioClient.listObjects(
					ListObjectsArgs.builder().bucket(bucket).build());
			for (Result<Item> result : objects) {
				String name = result.get().objectName();
				if (name.contains(prefix)) {
					return name;
				}
			}
		} catch (Exception e) {
			// ignore, retry
		}
		return null;
	}
}
