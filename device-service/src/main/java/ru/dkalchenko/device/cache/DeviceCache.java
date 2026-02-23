package ru.dkalchenko.device.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.properties.DeviceProperties;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Cache-aside паттерн для устройств в Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceCache {

	static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	String keyPrefix;
	long ttlSeconds;

	final StringRedisTemplate redisTemplate;
	final DeviceProperties properties;

	@PostConstruct
	public void init() {
		keyPrefix = properties.getCache().getKeyPrefix();
		ttlSeconds = properties.getCache().getTtlSeconds();
	}

	public Optional<Device> get(String deviceId) {
		String key = key(deviceId);
		String json = redisTemplate.opsForValue().get(key);
		if (json == null || json.isBlank()) {
			return Optional.empty();
		}
		try {
			Device d = parseDevice(json);
			return Optional.of(d);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	public void put(Device device) {
		String key = key(device.getDeviceId());
		try {
			String json = OBJECT_MAPPER.writeValueAsString(device);
			redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
		} catch (JsonProcessingException e) {
			log.info("Error while serializing device to JSON: deviceId = {}", device.getDeviceId(), e);
		}
	}

	public void invalidate(String deviceId) {
		String key = key(deviceId);
		redisTemplate.delete(key);
	}

	private Device parseDevice(String json) throws JsonProcessingException {
		var node = OBJECT_MAPPER.readTree(json);
		Device device = new Device();
		device.setDeviceId(node.get("deviceId").asText());
		device.setDeviceType(node.get("deviceType").asText());
		device.setCreatedAt(node.get("createdAt").asLong());
		device.setMeta(node.has("meta") && !node.get("meta").isNull() ? node.get("meta").asText() : null);
		device.setUpdatedAt(node.has("updatedAt") && !node.get("updatedAt").isNull()
				? OffsetDateTime.parse(node.get("updatedAt").asText())
				: OffsetDateTime.now());
		return device;
	}

	private String key(String deviceId) {
		return keyPrefix + deviceId;
	}
}
