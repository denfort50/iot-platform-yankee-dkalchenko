package ru.dkalchenko.device.service;

import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.api.model.MessageResponse;
import ru.dkalchenko.device.api.model.TargetVersionRequest;
import ru.dkalchenko.device.audit.AuditOutcome;
import ru.dkalchenko.device.audit.DeviceAuditEvent;
import ru.dkalchenko.device.audit.DeviceAuditService;
import ru.dkalchenko.device.audit.DeviceOperation;
import ru.dkalchenko.device.audit.AuditContextProvider;
import ru.dkalchenko.device.cache.DeviceCache;
import ru.dkalchenko.device.metrics.DeviceServiceMetrics;
import ru.dkalchenko.device.exception.TargetVersionConflictException;
import ru.dkalchenko.device.repository.DeviceRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Сервис управления устройствами: cache-aside READ из БД, CREATE/UPDATE/DELETE в Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceService {

	static SecureRandom RANDOM = new SecureRandom();
	static int DEVICE_ID_SUFFIX_MAX = 100_000_000;

	DeviceRepository deviceRepository;
	DeviceCache deviceCache;
	DevicePublisher devicePublisher;
	DeviceServiceMetrics metrics;
	DeviceAuditService deviceAuditService;
	AuditContextProvider auditContextProvider;
	StringRedisTemplate redisTemplate;

	private void audit(DeviceOperation operation, String deviceId, AuditOutcome outcome, String errorMessage) {
		deviceAuditService.record(DeviceAuditEvent.builder()
				.timestamp(Instant.now())
				.operation(operation)
				.deviceId(deviceId)
				.outcome(outcome)
				.actor(auditContextProvider.getCurrentActor())
				.traceId(auditContextProvider.getTraceId())
				.errorMessage(errorMessage)
				.build());
	}

	public Optional<Device> getDevice(String deviceId) {
		Timer.Sample sample = Timer.start();
		try {
			Optional<Device> cached = deviceCache.get(deviceId);
			if (cached.isPresent()) {
				MetaJson.enrichDeviceFromMeta(cached.get());
				metrics.recordSuccess("GET");
				audit(DeviceOperation.GET, deviceId, AuditOutcome.SUCCESS, null);
				sample.stop(metrics.getTimer("GET"));
				return cached;
			}
			Device fromDb = deviceRepository.findById(deviceId);
			if (fromDb != null) {
				MetaJson.enrichDeviceFromMeta(fromDb);
				deviceCache.put(fromDb);
				metrics.recordSuccess("GET");
				audit(DeviceOperation.GET, deviceId, AuditOutcome.SUCCESS, null);
			} else {
				audit(DeviceOperation.GET, deviceId, AuditOutcome.NOT_FOUND, null);
			}
			sample.stop(metrics.getTimer("GET"));
			return Optional.ofNullable(fromDb);
		} catch (Exception e) {
			metrics.recordFailure("GET");
			metrics.recordDbError("GET");
			audit(DeviceOperation.GET, deviceId, AuditOutcome.FAILURE, e.getMessage());
			sample.stop(metrics.getTimer("GET"));
			log.error("Error getting device: deviceId={}", deviceId, e);
			throw e;
		}
	}

	public String createDevice(DeviceRequest request) {
		Timer.Sample sample = Timer.start();
		String deviceId = "device-" + (RANDOM.nextInt(DEVICE_ID_SUFFIX_MAX) + 1);
		try {
			Device device = new Device()
					.deviceId(deviceId)
					.deviceType(request.getDeviceType())
					.createdAt(System.currentTimeMillis())
					.meta(request.getMeta())
					.updatedAt(OffsetDateTime.now());
			devicePublisher.sendCreate(device);
			metrics.recordSuccess("CREATE");
			audit(DeviceOperation.CREATE, deviceId, AuditOutcome.SUCCESS, null);
			log.info("Device create requested: deviceId={}", deviceId);
		} catch (Exception ex) {
			metrics.recordFailure("CREATE");
			audit(DeviceOperation.CREATE, deviceId, AuditOutcome.FAILURE, ex.getMessage());
			log.error("Error creating device: deviceId={}", deviceId, ex);
			throw new IllegalStateException("Device create failed", ex);
		} finally {
			sample.stop(metrics.getTimer("CREATE"));
		}
		return deviceId;
	}

	public void updateDevice(String deviceId, DeviceRequest request) {
		Timer.Sample sample = Timer.start();
		try {
			devicePublisher.sendUpdate(deviceId, request.getDeviceType(), request.getMeta());
			deviceCache.invalidate(deviceId);
			metrics.recordSuccess("UPDATE");
			audit(DeviceOperation.UPDATE, deviceId, AuditOutcome.SUCCESS, null);
			log.info("Device update requested: deviceId={}", deviceId);
		} catch (Exception e) {
			metrics.recordFailure("UPDATE");
			audit(DeviceOperation.UPDATE, deviceId, AuditOutcome.FAILURE, e.getMessage());
			log.error("Error updating device: deviceId={}", deviceId, e);
			throw e;
		} finally {
			sample.stop(metrics.getTimer("UPDATE"));
		}
	}

	/**
	 * Фиксирует целевую версию в meta и публикует UPDATE в Kafka.
	 */
	public ResponseEntity<MessageResponse> setDeviceTargetVersion(String deviceId, TargetVersionRequest request) {
		Timer.Sample sample = Timer.start();
		String target = request.getTargetVersion();
		String idemKey = request.getIdempotencyKey();
		try {
			if (idemKey != null && !idemKey.isBlank()) {
				String redisKey = "device:target-idem:" + deviceId + ":" + idemKey;
				String stored = redisTemplate.opsForValue().get(redisKey);
				if (stored != null) {
					if (stored.equals(target)) {
						return ResponseEntity.status(HttpStatus.ACCEPTED)
								.body(new MessageResponse()
										.message("Target version idempotent replay")
										.deviceId(deviceId));
					}
					throw new TargetVersionConflictException("Idempotency key reused with different targetVersion");
				}
			}
			Optional<Device> deviceOpt = getDevice(deviceId);
			if (deviceOpt.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			Device device = deviceOpt.get();
			String mergedMeta = MetaJson.mergeTargetSoftwareVersion(device.getMeta(), target);
			devicePublisher.sendUpdate(deviceId, device.getDeviceType(), mergedMeta);
			device.setMeta(mergedMeta);
			device.setTargetSoftwareVersion(target);
			device.setUpdatedAt(OffsetDateTime.now());
			deviceCache.put(device);
			if (idemKey != null && !idemKey.isBlank()) {
				redisTemplate.opsForValue().set(
						"device:target-idem:" + deviceId + ":" + idemKey,
						target,
						Duration.ofDays(7));
			}
			audit(DeviceOperation.UPDATE, deviceId, AuditOutcome.SUCCESS, null);
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new MessageResponse()
							.message("Target software version accepted")
							.deviceId(deviceId));
		} catch (TargetVersionConflictException e) {
			throw e;
		} catch (Exception e) {
			metrics.recordFailure("UPDATE");
			audit(DeviceOperation.UPDATE, deviceId, AuditOutcome.FAILURE, e.getMessage());
			log.error("Error setting target version: deviceId={}", deviceId, e);
			throw e;
		} finally {
			sample.stop(metrics.getTimer("UPDATE"));
		}
	}

	public void deleteDevice(String deviceId) {
		Timer.Sample sample = Timer.start();
		try {
			devicePublisher.sendDelete(deviceId);
			deviceCache.invalidate(deviceId);
			metrics.recordSuccess("DELETE");
			audit(DeviceOperation.DELETE, deviceId, AuditOutcome.SUCCESS, null);
			log.info("Device delete requested: deviceId={}", deviceId);
		} catch (Exception e) {
			metrics.recordFailure("DELETE");
			audit(DeviceOperation.DELETE, deviceId, AuditOutcome.FAILURE, e.getMessage());
			log.error("Error deleting device: deviceId={}", deviceId, e);
			throw e;
		} finally {
			sample.stop(metrics.getTimer("DELETE"));
		}
	}

}
