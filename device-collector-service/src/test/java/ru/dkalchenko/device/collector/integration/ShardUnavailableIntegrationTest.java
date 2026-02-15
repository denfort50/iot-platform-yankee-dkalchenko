package ru.dkalchenko.device.collector.integration;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.dkalchenko.device.collector.testcontainer.base.AbstractIntegrationTest;
import ru.dkalchenko.device.collector.testcontainer.containers.PostgresShardTestContainers;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Интеграционный тест: при недоступности одного из шардов после retry сообщение уходит в DLT.
 */
class ShardUnavailableIntegrationTest extends AbstractIntegrationTest {

	private static final String DEVICES_TOPIC = "devices.v1";
	private static final String DLT_TOPIC = "dlt.v1";

	@Autowired
	KafkaTemplate<String, GenericRecord> kafkaTemplate;
	@Autowired
	ConsumerFactory<String, GenericRecord> kafkaAvroConsumerFactory;

	@AfterEach
	void restartShardIfStopped() {
		if (!PostgresShardTestContainers.SHARD1.isRunning()) {
			PostgresShardTestContainers.SHARD1.start();
		}
	}

	@Test
	void whenTargetShardUnavailableThenRetriesAndMessageSentToDlt() throws Exception {
		int targetShard = 1;
		String deviceId = deviceIdForShard(targetShard);
		GenericRecord record = new GenericData.Record(getDeviceInfoSchema());
		record.put("deviceId", deviceId);
		record.put("deviceType", "sensor");
		record.put("createdAt", System.currentTimeMillis());
		record.put("meta", "{}");

		PostgreSQLContainer<?> shardToStop = PostgresShardTestContainers.SHARD1;
		shardToStop.stop();

		try {
			kafkaTemplate.send(DEVICES_TOPIC, deviceId, record).get(10, TimeUnit.SECONDS);

			String groupId = "dlt-shard-unavail-" + System.currentTimeMillis();
			try (var consumer = kafkaAvroConsumerFactory.createConsumer(groupId, null)) {
				consumer.subscribe(Collections.singletonList(DLT_TOPIC));
				consumer.poll(Duration.ofSeconds(2));

				// Первая попытка + 3 retry с таймаутом подключения 30s каждый → DLT может появиться через 2+ мин
				GenericRecord dltRecord = await()
						.atMost(180, TimeUnit.SECONDS)
						.pollInterval(2, TimeUnit.SECONDS)
						.until(() -> {
							var records = consumer.poll(Duration.ofSeconds(3)).records(DLT_TOPIC);
							return StreamSupport.stream(records.spliterator(), false)
									.map(org.apache.kafka.clients.consumer.ConsumerRecord::value)
									.filter(v -> v != null && deviceId.equals(
											v.get("originalKey") != null ? v.get("originalKey").toString() : null))
									.findFirst()
									.orElse(null);
						}, Objects::nonNull);

				assertThat(dltRecord).isNotNull();
				assertThat(dltRecord.get("originalTopic").toString()).isEqualTo(DEVICES_TOPIC);
				assertThat(dltRecord.get("originalKey").toString()).isEqualTo(deviceId);
				assertThat(dltRecord.get("errorMessage").toString()).isNotBlank();
			}
		} finally {
			shardToStop.start();
		}
	}

	private static String deviceIdForShard(int targetShard) {
		for (int i = 0; i < 1000; i++) {
			String id = "device-shard" + targetShard + "-" + i;
			if (expectedShardIndex(id) == targetShard) return id;
		}
		throw new IllegalStateException("No deviceId found for shard " + targetShard);
	}

	private static int expectedShardIndex(String deviceId) {
		if (deviceId == null || deviceId.isBlank()) return 0;
		return Math.abs(deviceId.hashCode() % 3);
	}
}
