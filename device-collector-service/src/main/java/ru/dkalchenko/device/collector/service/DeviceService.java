package ru.dkalchenko.device.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dkalchenko.device.collector.mapper.DeviceMapper;
import ru.dkalchenko.device.collector.model.Device;
import ru.dkalchenko.device.collector.model.DeviceUpdate;
import ru.dkalchenko.device.collector.repository.DeviceRepository;

import java.time.Instant;

/**
 * Сервис для обработки сообщений об устройствах из топика devices.v1.
 * Поддерживает операции CREATE (сохранение в шардированную БД), UPDATE (обновление записи), DELETE (удаление).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceService {

	DeviceRepository deviceRepository;
	DeviceMapper deviceMapper;

	/**
	 * Обрабатывает сообщение об устройстве в зависимости от поля operation.
	 * CREATE — сохраняет или обновляет запись (upsert); UPDATE — обновляет существующую; DELETE — удаляет.
	 *
	 * @param record GenericRecord из Kafka (схема DeviceInfo: operation, deviceId, deviceType, createdAt, meta)
	 * @throws IllegalArgumentException если данные некорректны
	 */
	@Transactional
	public void processDevice(GenericRecord record) {
		String operation = deviceMapper.getOperation(record);
		try {
			switch (operation) {
				case DeviceMapper.OP_CREATE -> doCreate(record);
				case DeviceMapper.OP_UPDATE -> doUpdate(record);
				case DeviceMapper.OP_DELETE -> doDelete(record);
				default -> {
					log.warn("Unknown operation '{}', treating as CREATE: deviceId={}", operation, deviceMapper.getDeviceId(record));
					doCreate(record);
				}
			}
		} catch (IllegalArgumentException e) {
			log.error("Invalid device data: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Error processing device: operation={}", operation, e);
			throw new RuntimeException("Failed to process device", e);
		}
	}

	private void doCreate(GenericRecord record) {
		Device device = deviceMapper.toDevice(record);
		log.trace("Creating/upserting device: deviceId={}, deviceType={}", device.deviceId(), device.deviceType());
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
	}

	private void doUpdate(GenericRecord record) {
		DeviceUpdate update = deviceMapper.toDeviceForUpdate(record);
		log.trace("Updating device: deviceId={}, deviceType={}", update.deviceId(), update.deviceType());
		int updated = deviceRepository.updateDevice(
				update.deviceId(),
				update.deviceType(),
				update.meta(),
				Instant.now()
		);
		if (updated > 0) {
			log.trace("Device updated successfully: deviceId={}", update.deviceId());
		} else {
			log.warn("Device update returned 0 rows (record may not exist): deviceId={}", update.deviceId());
		}
	}

	private void doDelete(GenericRecord record) {
		String deviceId = deviceMapper.getDeviceId(record);
		log.trace("Deleting device: deviceId={}", deviceId);
		int deleted = deviceRepository.deleteByDeviceId(deviceId);
		if (deleted > 0) {
			log.trace("Device deleted successfully: deviceId={}", deviceId);
		} else {
			log.warn("Device delete returned 0 rows (record may not exist): deviceId={}", deviceId);
		}
	}
}
