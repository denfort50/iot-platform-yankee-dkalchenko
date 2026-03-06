package ru.dkalchenko.device.collector.integration;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import ru.dkalchenko.device.collector.testcontainer.base.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Интеграционный тест: при ошибке обработки сообщение после retry отправляется в DLT,
 * содержимое DLT-сообщения соответствует схеме DeadLetterMessage.
 */
class DltIntegrationTest extends AbstractIntegrationTest {

	private static final String DEVICES_TOPIC = "devices.v1";
	private static final String DLT_TOPIC = "dlt.v1";

	@Autowired
	KafkaTemplate<String, GenericRecord> kafkaTemplate;
	@Autowired
	ConsumerFactory<String, GenericRecord> kafkaAvroConsumerFactory;

	@Test
	void whenProcessingFailsThenMessageSentToDltWithCorrectContent() throws Exception {
		// Сообщение с пустым deviceId — маппер выбросит IllegalArgumentException, после retry уйдёт в DLT
		GenericRecord invalidRecord = new GenericData.Record(getDeviceInfoSchema());
		invalidRecord.put("operation", "CREATE");
		invalidRecord.put("deviceId", "");
		invalidRecord.put("deviceType", "sensor");
		invalidRecord.put("createdAt", System.currentTimeMillis());
		invalidRecord.put("meta", "{}");

		String key = "invalid-key";
		kafkaTemplate.send(DEVICES_TOPIC, key, invalidRecord).get(10, TimeUnit.SECONDS);

		// Ждём появления сообщения в DLT (после retry)
		String groupId = "dlt-test-consumer-" + System.currentTimeMillis();
		try (var consumer = kafkaAvroConsumerFactory.createConsumer(groupId, null)) {
			consumer.subscribe(Collections.singletonList(DLT_TOPIC));
			consumer.poll(Duration.ofSeconds(2));

			GenericRecord dltRecord = await()
					.atMost(45, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						var records = consumer.poll(Duration.ofSeconds(3))
								.records(DLT_TOPIC);
						return StreamSupport.stream(records.spliterator(), false)
								.map(org.apache.kafka.clients.consumer.ConsumerRecord::value)
								.filter(v -> v != null && key.equals(
										v.get("originalKey") != null ? v.get("originalKey").toString() : null))
								.findFirst()
								.orElse(null);
					}, Objects::nonNull);

			assertThat(dltRecord).isNotNull();
			assertThat(dltRecord.getSchema()).isNotNull();
			assertThat(dltRecord.get("service").toString()).isEqualTo("device-collector-service");
			assertThat(dltRecord.get("originalTopic").toString()).isEqualTo(DEVICES_TOPIC);
			assertThat(dltRecord.get("originalKey").toString()).isEqualTo(key);
			assertThat(dltRecord.get("errorMessage")).isNotNull();
			assertThat(dltRecord.get("errorMessage").toString()).isNotBlank();
			assertThat(dltRecord.get("timestamp")).isNotNull();
			assertThat(dltRecord.get("id")).isNotNull();
			assertThat(dltRecord.get("id").toString()).isNotBlank();
			// originalValue — JSON тела исходного сообщения
			assertThat(dltRecord.get("originalValue").toString()).contains("deviceType");
		}
	}
}
