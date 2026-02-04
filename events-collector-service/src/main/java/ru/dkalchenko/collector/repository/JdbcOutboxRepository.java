package ru.dkalchenko.collector.repository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.collector.model.DeviceOutbox;

import java.util.List;

/**
 * JDBC-реализация записи и чтения таблицы collector.device_outbox в ClickHouse.
 * Создаётся, когда отсутствует бин {@link BatchedOutboxRepository} (при {@code collector.batch.enabled = false}).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnMissingBean(BatchedOutboxRepository.class)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JdbcOutboxRepository implements OutboxRepository {

    JdbcTemplate jdbcTemplate;

    @Override
    public void save(DeviceOutbox outbox) {
        jdbcTemplate.update(OutboxRepository.INSERT_SQL,
                outbox.deviceId(),
                OutboxRepositoryHelper.toClickHouseTimestamp(outbox.createdAt()),
                OutboxRepositoryHelper.getAttemptsOrZero(outbox),
                OutboxRepositoryHelper.getLastErrorOrEmpty(outbox)
        );
        log.trace("Saved outbox to ClickHouse: deviceId={}", outbox.deviceId());
    }

    @Override
    public List<DeviceOutbox> findPendingForShard(int totalShards, int shardIndex, int limit) {
        return OutboxRepositoryHelper.findPendingForShard(jdbcTemplate, totalShards, shardIndex, limit);
    }

    @Override
    public void markAsSent(String deviceId) {
        OutboxRepositoryHelper.markAsSent(jdbcTemplate, deviceId);
    }

    @Override
    public void incrementAttempts(String deviceId, String lastError) {
        OutboxRepositoryHelper.incrementAttempts(jdbcTemplate, deviceId, lastError);
    }

    @Override
    public long countPending() {
        return OutboxRepositoryHelper.countPending(jdbcTemplate);
    }
}
