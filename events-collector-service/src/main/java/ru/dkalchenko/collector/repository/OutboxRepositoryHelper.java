package ru.dkalchenko.collector.repository;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.dkalchenko.collector.model.DeviceOutbox;

import java.util.List;

/**
 * Общие SQL и RowMapper для репозиториев device_outbox. Используется {@link JdbcOutboxRepository}
 * и {@link BatchedOutboxRepository}.
 */
@Slf4j
@UtilityClass
public final class OutboxRepositoryHelper {

    static final String FIND_PENDING_SQL = """
            SELECT device_id, device_type, created_at, meta, attempts, last_error, trace_id, span_id
            FROM collector.device_outbox
            WHERE cityHash64(device_id) % ? = ?
            ORDER BY created_at
            LIMIT ?
            """;

    /** После успешной отправки в Kafka удаляем запись (нет записи = уже отправлено). */
    private static final String MARK_AS_SENT_SQL_TEMPLATE = """
            ALTER TABLE collector.device_outbox
            DELETE WHERE device_id = %s
            """;

    /** Инкрементирует счётчик попыток и сохраняет текст ошибки. */
    private static final String INCREMENT_ATTEMPTS_SQL_TEMPLATE = """
            ALTER TABLE collector.device_outbox
            UPDATE attempts = attempts + 1, last_error = %s
            WHERE device_id = %s
            """;

    static final RowMapper<DeviceOutbox> ROW_MAPPER = (rs, _) ->
            new DeviceOutbox(
                    rs.getString("device_id"),
                    rs.getString("device_type"),
                    rs.getLong("created_at"),
                    rs.getString("meta"),
                    rs.getInt("attempts"),
                    rs.getString("last_error"),
                    rs.getString("trace_id"),
                    rs.getString("span_id")
            );

    private static final String COUNT_PENDING_SQL = """
            SELECT count()
            FROM collector.device_outbox
            """;

    /** Возвращает lastError или пустую строку, если null. */
    public static String getLastErrorOrEmpty(DeviceOutbox outbox) {
        return outbox.lastError() != null ? outbox.lastError() : "";
    }

    /** Возвращает deviceType или пустую строку, если null. */
    public static String getDeviceTypeOrEmpty(DeviceOutbox outbox) {
        return outbox.deviceType() != null ? outbox.deviceType() : "";
    }

    /** Возвращает meta или пустую строку, если null. */
    public static String getMetaOrEmpty(DeviceOutbox outbox) {
        return outbox.meta() != null ? outbox.meta() : "";
    }

    /** Возвращает createdAt (epoch ms) или текущий момент, если null. */
    public static long getCreatedAtOrNow(DeviceOutbox outbox) {
        return outbox.createdAt() != null ? outbox.createdAt() : System.currentTimeMillis();
    }

    /** Возвращает attempts или 0, если null. */
    public static int getAttemptsOrZero(DeviceOutbox outbox) {
        return outbox.attempts() != null ? outbox.attempts() : 0;
    }

    /** Для INSERT: trace_id (nullable). */
    public static String getTraceIdOrNull(DeviceOutbox outbox) {
        return outbox.traceId();
    }

    /** Для INSERT: span_id (nullable). */
    public static String getSpanIdOrNull(DeviceOutbox outbox) {
        return outbox.spanId();
    }

    public static List<DeviceOutbox> findPendingForShard(JdbcTemplate jdbcTemplate,
                                                         int totalShards, int shardIndexOneBased, int limit) {
        if (totalShards <= 0) {
            return List.of();
        }
        // cityHash64 % N даёт 0..N-1; шард с номером 1..N соответствует остатку 0..N-1
        int remainder = shardIndexOneBased - 1;
        return jdbcTemplate.query(FIND_PENDING_SQL, ROW_MAPPER,
                totalShards, remainder, limit);
    }

    public static long countPending(JdbcTemplate jdbcTemplate) {
        try {
            Long count = jdbcTemplate.queryForObject(COUNT_PENDING_SQL, Long.class);
            long result = count != null ? count : 0L;
            log.trace("Outbox pending count: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to count pending outbox records: {}", e.getMessage());
            return 0L;
        }
    }

    static void markAsSent(JdbcTemplate jdbcTemplate, String deviceId) {
        String escaped = deviceId.replace("'", "''");
        String sql = MARK_AS_SENT_SQL_TEMPLATE.formatted("'" + escaped + "'");
        jdbcTemplate.update(sql);
        log.trace("Marked outbox as sent (deleted): deviceId={}", deviceId);
    }

    static void incrementAttempts(JdbcTemplate jdbcTemplate, String deviceId, String lastError) {
        String escapedDeviceId = deviceId.replace("'", "''");
        String escapedError = lastError != null ? lastError.replace("'", "''") : "";
        String sql = INCREMENT_ATTEMPTS_SQL_TEMPLATE.formatted(
                "'" + escapedError + "'",
                "'" + escapedDeviceId + "'"
        );
        jdbcTemplate.update(sql);
        log.trace("Incremented attempts for outbox: deviceId={}", deviceId);
    }
}
