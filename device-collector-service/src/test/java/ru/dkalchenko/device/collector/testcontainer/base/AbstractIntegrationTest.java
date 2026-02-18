package ru.dkalchenko.device.collector.testcontainer.base;

import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.device.collector.testcontainer.containers.KafkaTestContainer;
import ru.dkalchenko.device.collector.testcontainer.containers.PostgresShardTestContainers;
import ru.dkalchenko.device.collector.testcontainer.containers.SchemaRegistryTestContainer;
import ru.dkalchenko.device.collector.testcontainer.util.KafkaTestHelper;

import java.nio.charset.StandardCharsets;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	private static final String DEVICES_TOPIC = "devices.v1";

	private static Schema deviceInfoSchema;

	@BeforeEach
	void ensureDeviceInfoSchemaAndDevicesTopic() throws Exception {
		if (deviceInfoSchema == null) {
			String json = StreamUtils.copyToString(
					new ClassPathResource("avro/DeviceInfo.avsc").getInputStream(), StandardCharsets.UTF_8);
			deviceInfoSchema = new Schema.Parser().parse(json);
		}
		KafkaTestHelper.createTopicIfNeeded(DEVICES_TOPIC);
	}

	protected static Schema getDeviceInfoSchema() {
		return deviceInfoSchema;
	}

	private static String jdbcUrlWithSearchPath(PostgreSQLContainer<?> c) {
		String url = c.getJdbcUrl();
		String params = "currentSchema=collector&options=-c%20search_path%3Dcollector,pg_catalog,public";
		return url.contains("?") ? url + "&" + params : url + "?" + params;
	}

	@BeforeAll
	static void startContainers() {
		KafkaTestContainer.INSTANCE.start();
		SchemaRegistryTestContainer.INSTANCE.start();
		PostgresShardTestContainers.SHARD0.start();
		PostgresShardTestContainers.SHARD1.start();
		PostgresShardTestContainers.SHARD2.start();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers",
				() -> "localhost:" + KafkaTestContainer.EXTERNAL_PORT);
		registry.add("spring.kafka.properties.schema.registry.url",
				() -> "http://localhost:" + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081));

		String url0 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD0);
		String url1 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD1);
		String url2 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD2);
		String user = PostgresShardTestContainers.SHARD0.getUsername();
		String password = PostgresShardTestContainers.SHARD0.getPassword();

		registry.add("POSTGRESQL_DS0_URL", () -> url0);
		registry.add("POSTGRESQL_DS0_REPLICA_URL", () -> url0);
		registry.add("POSTGRESQL_DS1_URL", () -> url1);
		registry.add("POSTGRESQL_DS1_REPLICA_URL", () -> url1);
		registry.add("POSTGRESQL_DS2_URL", () -> url2);
		registry.add("POSTGRESQL_DS2_REPLICA_URL", () -> url2);
		registry.add("POSTGRESQL_DS0_USER", () -> user);
		registry.add("POSTGRESQL_DS0_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS0_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS0_REPLICA_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS1_USER", () -> user);
		registry.add("POSTGRESQL_DS1_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS1_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS1_REPLICA_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS2_USER", () -> user);
		registry.add("POSTGRESQL_DS2_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS2_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS2_REPLICA_PASSWORD", () -> password);
	}
}
