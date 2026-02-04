package ru.dkalchenko.collector.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.repository.OutboxRepository;
import ru.dkalchenko.collector.repository.OutboxRepositoryHelper;
import ru.dkalchenko.collector.testcontainer.base.AbstractIntegrationTest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест шардирования выборки из collector.device_outbox.
 * Проверяет, что findPendingForShard при totalShards = 2
 * разбивает записи на непересекающиеся подмножества, а их объединение даёт полный набор.
 */
class OutboxShardingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void findPendingForShardShouldDistributeRecordsBetweenShardsWithoutOverlap() {
        // given
        jdbcTemplate.update("TRUNCATE TABLE collector.device_outbox");

        var now = LocalDateTime.now().withNano(0);
        String[] deviceIds = {
                "device-a", "device-b", "device-c",
                "device-d", "device-e", "device-f"
        };

        for (String deviceId : deviceIds) {
            DeviceOutbox outbox = new DeviceOutbox(deviceId, now, 0, null);
            jdbcTemplate.update(OutboxRepository.INSERT_SQL,
                    outbox.deviceId(),
                    OutboxRepositoryHelper.toClickHouseTimestamp(outbox.createdAt()),
                    OutboxRepositoryHelper.getAttemptsOrZero(outbox),
                    OutboxRepositoryHelper.getLastErrorOrEmpty(outbox)
            );
        }

        int totalShards = 2;
        int limit = 100;

        // when
        List<DeviceOutbox> shard1 = OutboxRepositoryHelper
                .findPendingForShard(jdbcTemplate, totalShards, 1, limit);
        List<DeviceOutbox> shard2 = OutboxRepositoryHelper
                .findPendingForShard(jdbcTemplate, totalShards, 2, limit);

        // then
        Set<String> idsShard1 = shard1.stream().map(DeviceOutbox::deviceId).collect(HashSet::new, HashSet::add, HashSet::addAll);
        Set<String> idsShard2 = shard2.stream().map(DeviceOutbox::deviceId).collect(HashSet::new, HashSet::add, HashSet::addAll);

        // множества не пересекаются
        Set<String> intersection = new HashSet<>(idsShard1);
        intersection.retainAll(idsShard2);
        assertThat(intersection)
                .as("shards should not contain common device_ids")
                .isEmpty();

        // объединение даёт весь набор deviceIds
        Set<String> union = new HashSet<>(idsShard1);
        union.addAll(idsShard2);
        assertThat(union)
                .as("all inserted device_ids must be covered by shards")
                .containsExactlyInAnyOrder(deviceIds);
    }
}

