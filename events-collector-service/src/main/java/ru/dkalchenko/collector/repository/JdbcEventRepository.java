package ru.dkalchenko.collector.repository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.collector.model.DeviceEvent;

/**
 * Реализация репозитория событий на базе JDBC для записи в ClickHouse (таблица collector.device_events).
 * Создаётся, когда отсутствует бин {@link BatchedEventRepository} (при {@code collector.batch.enabled = false}).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnMissingBean(BatchedEventRepository.class)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JdbcEventRepository implements EventRepository {

    JdbcTemplate jdbcTemplate;

    @Override
    public void save(DeviceEvent event) {
        EventRepositoryHelper.insertOne(jdbcTemplate, event);
        log.trace("Saved event to ClickHouse: eventId={}, deviceId={}", event.eventId(), event.deviceId());
    }
}
