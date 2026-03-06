package ru.dkalchenko.failedevents.testcontainer.util;

import lombok.experimental.UtilityClass;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import ru.dkalchenko.failedevents.testcontainer.containers.KafkaTestContainer;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@UtilityClass
public final class KafkaTestHelper {

	public static void createTopic(String topic) {
		try (var admin = Admin.create(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
				"localhost:" + KafkaTestContainer.EXTERNAL_PORT))) {
			if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains(topic)) {
				admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
						.all().get(10, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to create topic " + topic, e);
		}
	}
}
