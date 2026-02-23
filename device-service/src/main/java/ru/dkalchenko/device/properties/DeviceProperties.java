package ru.dkalchenko.device.properties;

import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static lombok.AccessLevel.PRIVATE;

/**
 * Конфигурационные свойства для device-service.
 */
@Data
@ConfigurationProperties(prefix = "device-service")
@FieldDefaults(level = PRIVATE)
public class DeviceProperties {

	Kafka kafka = new Kafka();
	Cache cache = new Cache();
	Sharding sharding = new Sharding();

	@Data
	@FieldDefaults(level = PRIVATE)
	public static class Kafka {
		String deviceTopic = "devices.v1";
	}

	@Data
	@FieldDefaults(level = PRIVATE)
	public static class Cache {
		String keyPrefix = "device:";
		long ttlSeconds = 300;
	}

	@Data
	@FieldDefaults(level = PRIVATE)
	public static class Sharding {
		int shardingCount = 3;
	}
}
