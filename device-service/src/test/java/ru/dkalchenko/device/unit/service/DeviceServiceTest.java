package ru.dkalchenko.device.unit.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.audit.DeviceAuditService;
import ru.dkalchenko.device.audit.AuditContextProvider;
import ru.dkalchenko.device.cache.DeviceCache;
import ru.dkalchenko.device.metrics.DeviceServiceMetrics;
import ru.dkalchenko.device.repository.DeviceRepository;
import ru.dkalchenko.device.service.DevicePublisher;
import ru.dkalchenko.device.service.DeviceService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса устройств: cache-aside GET, CREATE/UPDATE/DELETE через Kafka, аудит и метрики.
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

	static final String DEVICE_ID = "device-001";
	static final String DEVICE_TYPE = "sensor";
	static final long CREATED_AT = 1_700_000_000_000L;
	static final String META = "{}";

	@Mock
	DeviceRepository deviceRepository;
	@Mock
	DeviceCache deviceCache;
	@Mock
	DevicePublisher devicePublisher;
	@Mock
	DeviceAuditService deviceAuditService;
	@Mock
	AuditContextProvider auditContextProvider;
	@Mock
	StringRedisTemplate redisTemplate;
	@Mock
	ValueOperations<String, String> valueOperations;

	DeviceServiceMetrics metrics;
	DeviceService deviceService;

	@BeforeEach
	void setUp() {
		metrics = new DeviceServiceMetrics(new SimpleMeterRegistry());
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		deviceService = new DeviceService(
				deviceRepository,
				deviceCache,
				devicePublisher,
				metrics,
				deviceAuditService,
				auditContextProvider,
				redisTemplate
		);
	}

	@Test
	void whenGetDeviceWhenCacheHitThenReturnsCachedAndRecordsSuccess() {
		Device device = new Device()
				.deviceId(DEVICE_ID)
				.deviceType(DEVICE_TYPE)
				.createdAt(CREATED_AT)
				.meta(META)
				.updatedAt(OffsetDateTime.now());
		when(deviceCache.get(DEVICE_ID)).thenReturn(Optional.of(device));

		Optional<Device> result = deviceService.getDevice(DEVICE_ID);

		assertThat(result).isPresent().get().extracting(Device::getDeviceId, Device::getDeviceType).containsExactly(DEVICE_ID, DEVICE_TYPE);
		verify(deviceCache).get(DEVICE_ID);
		verify(deviceRepository, never()).findById(any());
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenGetDeviceWhenCacheMissAndFoundInDbThenReturnsDeviceAndPutsInCache() {
		when(deviceCache.get(DEVICE_ID)).thenReturn(Optional.empty());
		Device fromDb = new Device()
				.deviceId(DEVICE_ID)
				.deviceType(DEVICE_TYPE)
				.createdAt(CREATED_AT)
				.meta(META)
				.updatedAt(OffsetDateTime.now());
		when(deviceRepository.findById(DEVICE_ID)).thenReturn(fromDb);

		Optional<Device> result = deviceService.getDevice(DEVICE_ID);

		assertThat(result).isPresent().get().isEqualTo(fromDb);
		verify(deviceCache).put(fromDb);
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenGetDeviceWhenCacheMissAndNotFoundInDbThenReturnsEmptyAndAuditsNotFound() {
		when(deviceCache.get(DEVICE_ID)).thenReturn(Optional.empty());
		when(deviceRepository.findById(DEVICE_ID)).thenReturn(null);

		Optional<Device> result = deviceService.getDevice(DEVICE_ID);

		assertThat(result).isEmpty();
		verify(deviceCache, never()).put(any());
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenGetDeviceWhenRepositoryThrowsThenPropagatesAndRecordsFailure() {
		when(deviceCache.get(DEVICE_ID)).thenReturn(Optional.empty());
		when(deviceRepository.findById(DEVICE_ID)).thenThrow(new RuntimeException("DB error"));

		assertThatThrownBy(() -> deviceService.getDevice(DEVICE_ID))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("DB error");

		verify(deviceAuditService).record(any());
	}

	@Test
	void whenCreateDevicThenSendsCreateAndReturnsDeviceId() {
		DeviceRequest request = new DeviceRequest().deviceType(DEVICE_TYPE).meta(META);
		doNothing().when(devicePublisher).sendCreate(any(Device.class));

		String deviceId = deviceService.createDevice(request);

		assertThat(deviceId).startsWith("device-");
		ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
		verify(devicePublisher).sendCreate(deviceCaptor.capture());
		Device sent = deviceCaptor.getValue();
		assertThat(sent.getDeviceId()).isEqualTo(deviceId);
		assertThat(sent.getDeviceType()).isEqualTo(DEVICE_TYPE);
		assertThat(sent.getMeta()).isEqualTo(META);
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenCreateDeviceWhenPublisherThrowsThenWrapsInIllegalStateAndAuditsFailure() {
		DeviceRequest request = new DeviceRequest().deviceType(DEVICE_TYPE).meta(META);
		doThrow(new RuntimeException("Kafka unavailable")).when(devicePublisher).sendCreate(any(Device.class));

		assertThatThrownBy(() -> deviceService.createDevice(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Device create failed")
				.hasCauseInstanceOf(RuntimeException.class);

		verify(deviceAuditService).record(any());
	}

	@Test
	void whenUpdateDeviceThenInvalidatesCacheAndSendsUpdate() {
		DeviceRequest request = new DeviceRequest().deviceType("actuator").meta("{\"x\":1}");

		deviceService.updateDevice(DEVICE_ID, request);

		verify(devicePublisher).sendUpdate(eq(DEVICE_ID), eq("actuator"), eq("{\"x\":1}"));
		verify(deviceCache).invalidate(DEVICE_ID);
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenUpdateDeviceWhenPublisherThrowsThenPropagatesAndAuditsFailure() {
		DeviceRequest request = new DeviceRequest().deviceType(DEVICE_TYPE).meta(META);
		doThrow(new RuntimeException("Send failed")).when(devicePublisher).sendUpdate(eq(DEVICE_ID), any(), any());

		assertThatThrownBy(() -> deviceService.updateDevice(DEVICE_ID, request))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Send failed");

		verify(deviceAuditService).record(any());
	}

	@Test
	void whenDeleteDeviceThenInvalidatesCacheAndSendsDelete() {
		deviceService.deleteDevice(DEVICE_ID);

		verify(devicePublisher).sendDelete(DEVICE_ID);
		verify(deviceCache).invalidate(DEVICE_ID);
		verify(deviceAuditService).record(any());
	}

	@Test
	void whenDeleteDeviceWhenPublisherThrowsThenPropagatesAndAuditsFailure() {
		doThrow(new RuntimeException("Kafka error")).when(devicePublisher).sendDelete(DEVICE_ID);

		assertThatThrownBy(() -> deviceService.deleteDevice(DEVICE_ID))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Kafka error");

		verify(deviceAuditService).record(any());
	}
}
