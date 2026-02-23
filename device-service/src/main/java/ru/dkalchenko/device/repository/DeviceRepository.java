package ru.dkalchenko.device.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.device.api.model.Device;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Репозиторий для чтения устройств из шардированной БД.
 */
@Repository
@RequiredArgsConstructor
public class DeviceRepository {

	private static final String SELECT_BY_ID = """
			SELECT device_id, device_type, created_at, meta, updated_at
			FROM devices
			WHERE device_id = ?
			""";

	private static final RowMapper<Device> ROW_MAPPER = (rs, rowNum) -> {
		Device device = new Device();
		device.setDeviceId(rs.getString("device_id"));
		device.setDeviceType(rs.getString("device_type"));
		device.setCreatedAt(rs.getLong("created_at"));
		device.setMeta(rs.getString("meta"));
		Timestamp updatedAtTs = rs.getTimestamp("updated_at");
		device.setUpdatedAt(updatedAtTs != null ? updatedAtTs.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
		return device;
	};

	private final JdbcTemplate jdbcTemplate;

	public Device findById(String deviceId) {
		return jdbcTemplate.query(SELECT_BY_ID, ROW_MAPPER, deviceId)
				.stream()
				.findFirst()
				.orElse(null);
	}
}
