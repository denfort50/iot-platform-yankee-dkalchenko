package ru.dkalchenko.collector.repository;

import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.collector.model.DeviceEvent;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Общая логика привязки параметров DeviceEvent для {@link JdbcEventRepository}
 * и {@link BatchedEventRepository}.
 */
@UtilityClass
final class EventRepositoryHelper {

    /**
     * Вставляет одно событие через переданный INSERT_SQL.
     */
    static void insertOne(JdbcTemplate jdbcTemplate, DeviceEvent event) {
        long timestampMs = event.timestamp() != null ? event.timestamp() : System.currentTimeMillis();
        Date eventDate = Date.valueOf(
                Instant.ofEpochMilli(timestampMs).atZone(ZoneOffset.UTC).toLocalDate()
        );
        jdbcTemplate.update(EventRepository.INSERT_SQL,
                event.deviceId(),
                event.eventId(),
                eventDate,
                timestampMs,
                event.type(),
                event.payload()
        );
    }

    /**
     * Привязывает параметры события к PreparedStatement (позиции 1–6).
     */
    static void setValues(PreparedStatement ps, DeviceEvent event) throws SQLException {
        long timestampMs = event.timestamp() != null ? event.timestamp() : System.currentTimeMillis();
        Date eventDate = Date.valueOf(
                Instant.ofEpochMilli(timestampMs).atZone(ZoneOffset.UTC).toLocalDate()
        );
        ps.setString(1, event.deviceId());
        ps.setString(2, event.eventId());
        ps.setDate(3, eventDate);
        ps.setLong(4, timestampMs);
        ps.setString(5, event.type());
        ps.setString(6, event.payload());
    }
}
