package ru.dkalchenko.device.collector.properties;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурационные свойства для device-collector-service.
 */
@Data
@ConfigurationProperties(prefix = "device-collector")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceCollectorProperties {

	Publisher publisher = new Publisher();
	Sharding sharding = new Sharding();

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	public static class Listener {
		String kafkaTopic = "devices.v1";
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	public static class Publisher {
		String dltTopic = "dlt.v1";
		int dltTopicPartitions = 3;
		int dltTopicReplicas = 2;
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	public static class Sharding {
		int shardingCount = 3;
	}
}
