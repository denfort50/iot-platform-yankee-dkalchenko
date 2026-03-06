package ru.dkalchenko.collector.repository;

import ru.dkalchenko.collector.model.DeviceOutbox;

import java.util.List;

/**
 * Репозиторий для записи и чтения записей outbox в таблицу collector.device_outbox (ClickHouse).
 */
public interface OutboxRepository {

    String INSERT_SQL = """
            INSERT INTO collector.device_outbox (operation, device_id, device_type, created_at, meta, attempts, last_error, trace_id, span_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Сохраняет запись в таблицу collector.device_outbox.
     *
     * @param outbox запись outbox (новый device для последующей отправки)
     */
    void save(DeviceOutbox outbox);

    /**
     * Выбирает записи со статусом NEW для заданного шарда (распределённая выборка по инстансам).
     *
     * @param totalShards общее число шардов (инстансов)
     * @param shardIndex  номер шарда этого инстанса (1..totalShards)
     * @param limit       максимум записей
     * @return список записей для отправки
     */
    List<DeviceOutbox> findPendingForShard(int totalShards, int shardIndex, int limit);

    /**
     * Удаляет запись после успешной отправки в Kafka (нет записи = уже отправлено).
     *
     * @param deviceId device_id записи
     */
    void markAsSent(String deviceId);

    /**
     * Инкрементирует счётчик попыток и сохраняет текст ошибки при неудачной отправке.
     *
     * @param deviceId  device_id записи
     * @param lastError текст ошибки (может быть null)
     */
    void incrementAttempts(String deviceId, String lastError);

    /**
     * Возвращает количество записей в outbox, требующих обработки.
     * В текущей реализации это просто количество строк в таблице collector.device_outbox.
     */
    long countPending();
}
