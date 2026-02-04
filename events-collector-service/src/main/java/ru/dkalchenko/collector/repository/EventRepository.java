package ru.dkalchenko.collector.repository;

import ru.dkalchenko.collector.model.DeviceEvent;

/**
 * Репозиторий для сохранения событий устройств в ClickHouse.
 */
public interface EventRepository {

    String INSERT_SQL = """
            INSERT INTO collector.device_events (device_id, event_id, event_date, timestamp_ms, type, payload)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    /**
     * Сохраняет событие устройства в таблицу collector.device_events.
     *
     * @param event событие для сохранения
     */
    void save(DeviceEvent event);
}
