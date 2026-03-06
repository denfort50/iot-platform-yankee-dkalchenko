package ru.dkalchenko.failedevents.testcontainer.base;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StreamUtils;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.failedevents.testcontainer.config.IntegrationTestKafkaConfig;
import ru.dkalchenko.failedevents.testcontainer.containers.KafkaTestContainer;
import ru.dkalchenko.failedevents.testcontainer.containers.MinioTestContainer;
import ru.dkalchenko.failedevents.testcontainer.containers.SchemaRegistryTestContainer;
import ru.dkalchenko.failedevents.testcontainer.util.KafkaTestHelper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Базовый класс интеграционных тестов: Kafka, Schema Registry, MinIO.
 * Перед тестами создаётся топик dlt.v1. Тесты используют KafkaTemplate для отправки DeadLetterMessage.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(IntegrationTestKafkaConfig.class)
public abstract class AbstractIntegrationTest {

	private static final String DLT_TOPIC = "dlt.v1";
	private static Schema deadLetterMessageSchema;

	@BeforeEach
	void ensureDltTopic() {
		KafkaTestHelper.createTopic(DLT_TOPIC);
	}

	@BeforeAll
	static void startContainers() {
		KafkaTestContainer.INSTANCE.start();
		SchemaRegistryTestContainer.INSTANCE.start();
		MinioTestContainer.INSTANCE.start();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers",
				() -> "localhost:" + KafkaTestContainer.EXTERNAL_PORT);
		registry.add("spring.kafka.properties.schema.registry.url",
				() -> "http://localhost:" + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081));
		registry.add("spring.kafka.producer.properties.schema.registry.url",
				() -> "http://localhost:" + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081));

		registry.add("failed-events-processor.minio.endpoint",
				() -> "http://" + MinioTestContainer.INSTANCE.getHost() + ":" + MinioTestContainer.INSTANCE.getMappedPort(9000));
		registry.add("failed-events-processor.minio.access-key", () -> "minioadmin");
		registry.add("failed-events-processor.minio.secret-key", () -> "minioadmin");
	}

	protected static String getDltTopic() {
		return DLT_TOPIC;
	}

	protected static Schema getDeadLetterMessageSchema() {
		if (deadLetterMessageSchema == null) {
			try {
				String json = StreamUtils.copyToString(
						new ClassPathResource("avro/DeadLetterMessage.avsc").getInputStream(),
						StandardCharsets.UTF_8);
				deadLetterMessageSchema = new Schema.Parser().parse(json);
			} catch (Exception e) {
				throw new IllegalStateException("Failed to load DeadLetterMessage.avsc", e);
			}
		}
		return deadLetterMessageSchema;
	}

	/**
	 * Собирает Avro-запись DeadLetterMessage для отправки в DLT.
	 */
	protected static GenericRecord buildDeadLetterMessage(String originalValueJson, String service, String errorMessage) {
		GenericRecord msg = new GenericData.Record(getDeadLetterMessageSchema());
		msg.put("id", UUID.randomUUID().toString());
		msg.put("service", service);
		msg.put("originalTopic", "devices.v1");
		msg.put("timestamp", System.currentTimeMillis());
		msg.put("errorMessage", errorMessage);
		msg.put("stackTrace", "");
		msg.put("traceId", "0123456789abcdef0123456789abcdef");
		msg.put("originalKey", "test-key");
		msg.put("originalValue", originalValueJson);
		return msg;
	}
}
