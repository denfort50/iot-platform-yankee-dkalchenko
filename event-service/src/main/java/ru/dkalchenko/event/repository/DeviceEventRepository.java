package ru.dkalchenko.event.repository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.event.api.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Чтение событий из ClickHouse.
 */
@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceEventRepository {

	JdbcTemplate jdbcTemplate;

	static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> new Event()
			.eventId(rs.getString("event_id"))
			.deviceId(rs.getString("device_id"))
			.timestamp(rs.getLong("timestamp_ms"))
			.type(rs.getString("type"))
			.payload(rs.getString("payload"));

	public long count(String deviceId, Long fromTimestamp, Long toTimestamp, String type) {
		StringBuilder sql = new StringBuilder("SELECT count() FROM collector.device_events WHERE device_id = ?");
		List<Object> args = new ArrayList<>();
		args.add(deviceId);
		appendTimeAndType(sql, args, fromTimestamp, toTimestamp, type);
		Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
		return total != null ? total : 0L;
	}

	public boolean existsDevice(String deviceId) {
		String sql = "SELECT 1 FROM collector.device_events WHERE device_id = ? LIMIT 1";
		List<Integer> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1), deviceId);
		return !rows.isEmpty();
	}

	public List<Event> findPage(String deviceId, Long fromTimestamp, Long toTimestamp, String type, int offset, int limit) {
		StringBuilder sql = new StringBuilder("""
				SELECT event_id, device_id, timestamp_ms, type, payload
				FROM collector.device_events
				WHERE device_id = ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(deviceId);
		appendTimeAndType(sql, args, fromTimestamp, toTimestamp, type);
		sql.append(" ORDER BY timestamp_ms DESC, event_id DESC LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
	}

	public Optional<Event> findByDeviceAndEventId(String deviceId, String eventId) {
		String sql = """
				SELECT event_id, device_id, timestamp_ms, type, payload
				FROM collector.device_events
				WHERE device_id = ? AND event_id = ?
				LIMIT 1
				""";
		List<Event> rows = jdbcTemplate.query(sql, ROW_MAPPER, deviceId, eventId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(rows.getFirst());
	}

	private static void appendTimeAndType(StringBuilder sql, List<Object> args,
			Long fromTimestamp, Long toTimestamp, String type) {
		if (fromTimestamp != null) {
			sql.append(" AND timestamp_ms >= ?");
			args.add(fromTimestamp);
		}
		if (toTimestamp != null) {
			sql.append(" AND timestamp_ms <= ?");
			args.add(toTimestamp);
		}
		if (type != null && !type.isBlank()) {
			sql.append(" AND type = ?");
			args.add(type);
		}
	}
}
