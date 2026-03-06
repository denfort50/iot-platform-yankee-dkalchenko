package ru.dkalchenko.device.collector.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Репозиторий для работы с устройствами.
 * Использует ON CONFLICT (device_id) DO UPDATE для идемпотентности.
 */
@Repository
@RequiredArgsConstructor
public class DeviceRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO devices (device_id, device_type, created_at, meta, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (device_id) DO UPDATE SET
            	device_type = EXCLUDED.device_type,
            	created_at = EXCLUDED.created_at,
            	meta = EXCLUDED.meta,
            	updated_at = EXCLUDED.updated_at
            """;

    private static final String UPDATE_SQL = """
            UPDATE devices SET device_type = ?, meta = ?, updated_at = ?
            WHERE device_id = ?
            """;

    private static final String DELETE_SQL = """
            DELETE FROM devices WHERE device_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Сохраняет или обновляет устройство (CREATE).
     * Идемпотентная операция: при том же device_id запись обновляется.
     */
    public int upsertDevice(String deviceId, String deviceType, Long createdAt, String meta, Instant updatedAt) {
        return jdbcTemplate.update(UPSERT_SQL, deviceId, deviceType, createdAt, meta, Timestamp.from(updatedAt));
    }

    /**
     * Обновляет существующую запись устройства (UPDATE).
     *
     * @return количество обновлённых строк (0 или 1)
     */
    public int updateDevice(String deviceId, String deviceType, String meta, Instant updatedAt) {
        return jdbcTemplate.update(UPDATE_SQL, deviceType, meta, Timestamp.from(updatedAt), deviceId);
    }

    /**
     * Удаляет запись устройства по device_id (DELETE).
     *
     * @return количество удалённых строк (0 или 1)
     */
    public int deleteByDeviceId(String deviceId) {
        return jdbcTemplate.update(DELETE_SQL, deviceId);
    }
}
