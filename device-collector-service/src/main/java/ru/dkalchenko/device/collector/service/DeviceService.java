package ru.dkalchenko.device.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dkalchenko.device.collector.mapper.DeviceMapper;
import ru.dkalchenko.device.collector.model.Device;
import ru.dkalchenko.device.collector.repository.DeviceRepository;

import java.time.Instant;

/**
 * Сервис для обработки устройств.
 * Обеспечивает идемпотентность через ON CONFLICT DO UPDATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceService {

	DeviceRepository deviceRepository;
	DeviceMapper deviceMapper;

	/**
	 * Сохраняет или обновляет устройство из Kafka сообщения.
	 * Идемпотентная операция: повторная обработка того же сообщения не создаст дубликат.
	 * 
	 * @param record GenericRecord из Kafka
	 * @throws IllegalArgumentException если данные некорректны
	 */
	@Transactional
	public void upsertDevice(org.apache.avro.generic.GenericRecord record) {
		try {
			Device device = deviceMapper.toDevice(record);
			log.trace("Upserting device: deviceId={}, deviceType={}",
					device.deviceId(), device.deviceType());

			int updated = deviceRepository.upsertDevice(
					device.deviceId(),
					device.deviceType(),
					device.createdAt(),
					device.meta(),
					Instant.now()
			);

			if (updated > 0) {
				log.trace("Device upserted successfully: deviceId={}", device.deviceId());
			} else {
				log.warn("Device upsert returned 0 rows: deviceId={}", device.deviceId());
			}
		} catch (IllegalArgumentException e) {
			log.error("Invalid device data: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Error upserting device", e);
			throw new RuntimeException("Failed to upsert device", e);
		}
	}
}
