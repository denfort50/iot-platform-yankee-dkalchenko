package ru.dkalchenko.device.collector.integration;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.dkalchenko.device.collector.testcontainer.base.AbstractIntegrationTest;
import ru.dkalchenko.device.collector.testcontainer.containers.PostgresShardTestContainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Полный интеграционный тест: Kafka + Schema Registry + PostgreSQL (3 шарда) + ShardingSphere.
 * Проверяет: публикация Avro в devices.v1 → потребление сервисом → десериализация → маршрутизация по shard-ключу →
 * INSERT в нужный шард → повторная отправка (UPDATE, идемпотентность).
 */
class FullIntegrationTest extends AbstractIntegrationTest {

	private static final String DEVICES_TOPIC = "devices.v1";
	private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final String TRACEPARENT_HEADER = "traceparent";
	/** W3C: 00-{traceId 32 hex}-{spanId 16 hex}-01 */
	private static String traceparent(String traceId32Hex, String spanId16Hex) {
		return "00-" + traceId32Hex + "-" + spanId16Hex + "-01";
	}

	@Autowired
	KafkaTemplate<String, GenericRecord> kafkaTemplate;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void fullProcessIntegrationTest() throws Exception {
		String deviceId = "device-" + UUID.randomUUID();
		String deviceType = "sensor";
		long createdAt = System.currentTimeMillis();
		String meta = "{\"location\":\"room1\"}";
		String traceId32 = UUID.randomUUID().toString().replace("-", "");
		String spanId16 = "0000000000000000";

		// 1. Публикация Avro в devices.v1 с заголовком traceparent (W3C)
		GenericRecord record = deviceRecord(getDeviceInfoSchema(), deviceId, deviceType, createdAt, meta);
		ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(DEVICES_TOPIC, deviceId, record);
		producerRecord.headers().add(TRACEPARENT_HEADER, traceparent(traceId32, spanId16).getBytes(StandardCharsets.UTF_8));
		SendResult<String, GenericRecord> sendResult = kafkaTemplate.send(producerRecord)
				.get(10, TimeUnit.SECONDS);
		assertThat(sendResult.getRecordMetadata().topic()).isEqualTo(DEVICES_TOPIC);

		// 2. Ожидание: сообщение прочитано сервисом и запись появилась в БД (через ShardingSphere)
		await().atMost(AWAIT_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
				.pollInterval(500, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> {
					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM devices WHERE device_id = ?",
							Integer.class, deviceId);
					assertThat(count).isNotNull().isGreaterThan(0);
				});

		// 3. Проверка корректной маршрутизации: запись только в одном шарде (по shard-ключу device_id)
		int expectedShard = expectedShardIndex(deviceId);
		for (int i = 0; i < 3; i++) {
			JdbcTemplate shardJdbc = jdbcForShard(i);
			Integer count = shardJdbc.queryForObject(
					"SELECT COUNT(*) FROM collector.devices WHERE device_id = ?",
					Integer.class, deviceId);
			assertThat(count).as("shard " + i + " count for " + deviceId).isEqualTo(i == expectedShard ? 1 : 0);
		}

		// 4. Проверка содержимого после INSERT
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT device_id, device_type, created_at, meta FROM devices WHERE device_id = ?",
				deviceId);
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().get("device_id")).isEqualTo(deviceId);
		assertThat(rows.getFirst().get("device_type")).isEqualTo(deviceType);
		assertThat(rows.getFirst().get("created_at")).isEqualTo(createdAt);
		assertThat(rows.getFirst().get("meta")).isEqualTo(meta);

		// 5. Повторная отправка того же device_id (UPDATE, идемпотентность), с другим traceparent
		String updatedType = "actuator";
		String updatedMeta = "{\"location\":\"hall\"}";
		long updatedCreatedAt = createdAt + 1000;
		String updateTraceId32 = UUID.randomUUID().toString().replace("-", "");
		GenericRecord updateRecord = deviceRecord(getDeviceInfoSchema(), deviceId, updatedType, updatedCreatedAt, updatedMeta);
		ProducerRecord<String, GenericRecord> updateProducerRecord = new ProducerRecord<>(DEVICES_TOPIC, deviceId, updateRecord);
		updateProducerRecord.headers().add(TRACEPARENT_HEADER, traceparent(updateTraceId32, spanId16).getBytes(StandardCharsets.UTF_8));
		kafkaTemplate.send(updateProducerRecord).get(10, TimeUnit.SECONDS);

		await().atMost(AWAIT_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
				.pollInterval(500, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> {
					List<Map<String, Object>> updated = jdbcTemplate.queryForList(
							"SELECT device_type, meta, created_at FROM devices WHERE device_id = ?",
							deviceId);
					assertThat(updated).hasSize(1);
					assertThat(updated.getFirst().get("device_type")).isEqualTo(updatedType);
					assertThat(updated.getFirst().get("meta")).isEqualTo(updatedMeta);
					assertThat(updated.getFirst().get("created_at")).isEqualTo(updatedCreatedAt);
				});

		// 6. Идемпотентность: по-прежнему одна запись в одном шарде
		Integer totalRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM devices WHERE device_id = ?",
				Integer.class, deviceId);
		assertThat(totalRows).isEqualTo(1);
		Integer countInExpectedShard = jdbcForShard(expectedShard).queryForObject(
				"SELECT COUNT(*) FROM collector.devices WHERE device_id = ?",
				Integer.class, deviceId);
		assertThat(countInExpectedShard).isEqualTo(1);
	}

	private GenericRecord deviceRecord(Schema schema, String deviceId, String deviceType, long createdAt, String meta) {
		GenericRecord record = new GenericData.Record(schema);
		record.put("deviceId", deviceId);
		record.put("deviceType", deviceType);
		record.put("createdAt", createdAt);
		record.put("meta", meta);
		return record;
	}

	private static int expectedShardIndex(String deviceId) {
		if (deviceId == null || deviceId.isBlank()) return 0;
		return Math.abs(deviceId.hashCode() % 3);
	}

	private JdbcTemplate jdbcForShard(int shardIndex) {
		PostgreSQLContainer<?> c = switch (shardIndex) {
			case 0 -> PostgresShardTestContainers.SHARD0;
			case 1 -> PostgresShardTestContainers.SHARD1;
			case 2 -> PostgresShardTestContainers.SHARD2;
			default -> throw new IllegalArgumentException("shard " + shardIndex);
		};
		String url = c.getJdbcUrl();
		String params = "currentSchema=collector";
		String fullUrl = url.contains("?") ? url + "&" + params : url + "?" + params;
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(fullUrl);
		ds.setUsername(c.getUsername());
		ds.setPassword(c.getPassword());
		ds.setDriverClassName("org.postgresql.Driver");
		return new JdbcTemplate(ds);
	}
}
