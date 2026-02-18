package ru.dkalchenko.collector.repository;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.properties.BatchProperties;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Репозиторий outbox с батчевой записью в ClickHouse.
 * Гибридная стратегия: сброс при достижении {@link BatchProperties#batchSize()}
 * или по таймауту {@link BatchProperties#flushIntervalMs()}.
 */
@Slf4j
@Repository
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "collector.batch.enabled", havingValue = "true")
public class BatchedOutboxRepository extends AbstractBatchedRepository<DeviceOutbox> implements OutboxRepository {

    final JdbcTemplate jdbcTemplate;

    public BatchedOutboxRepository(JdbcTemplate jdbcTemplate, BatchProperties batchProperties) {
        super(batchProperties, "outbox-batch-flush");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(DeviceOutbox outbox) {
        addAndFlushIfFull(outbox);
    }

    @Override
    protected String getFlushLogName() {
        return "outbox records";
    }

    @Override
    protected void doFlush(List<DeviceOutbox> batch) {
        jdbcTemplate.batchUpdate(OutboxRepository.INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DeviceOutbox o = batch.get(i);
                ps.setString(1, o.deviceId());
                ps.setString(2, OutboxRepositoryHelper.getDeviceTypeOrEmpty(o));
                ps.setLong(3, OutboxRepositoryHelper.getCreatedAtOrNow(o));
                ps.setString(4, OutboxRepositoryHelper.getMetaOrEmpty(o));
                ps.setInt(5, OutboxRepositoryHelper.getAttemptsOrZero(o));
                ps.setString(6, OutboxRepositoryHelper.getLastErrorOrEmpty(o));
                ps.setString(7, OutboxRepositoryHelper.getTraceIdOrNull(o));
                ps.setString(8, OutboxRepositoryHelper.getSpanIdOrNull(o));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
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
