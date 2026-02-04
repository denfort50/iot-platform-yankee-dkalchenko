package ru.dkalchenko.collector.repository;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.properties.BatchProperties;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Репозиторий событий с батчевой записью в ClickHouse.
 * Гибридная стратегия: сброс при достижении {@link BatchProperties#batchSize()}
 * или по таймауту {@link BatchProperties#flushIntervalMs()}.
 */
@Slf4j
@Repository
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "collector.batch.enabled", havingValue = "true")
public class BatchedEventRepository extends AbstractBatchedRepository<DeviceEvent> implements EventRepository {

    final JdbcTemplate jdbcTemplate;

    public BatchedEventRepository(JdbcTemplate jdbcTemplate, BatchProperties batchProperties) {
        super(batchProperties, "clickhouse-batch-flush");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(DeviceEvent event) {
        addAndFlushIfFull(event);
    }

    @Override
    protected String getFlushLogName() {
        return "events";
    }

    @Override
    protected void doFlush(List<DeviceEvent> batch) {
        jdbcTemplate.batchUpdate(EventRepository.INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EventRepositoryHelper.setValues(ps, batch.get(i));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }
}
