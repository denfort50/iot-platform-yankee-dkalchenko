package ru.dkalchenko.collector.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства батчевой записи в ClickHouse.
 * Гибридная стратегия: сброс при достижении batchSize или по истечении flushIntervalMs.
 */
@ConfigurationProperties(prefix = "collector.batch")
public record BatchProperties(
        Boolean enabled,
        int batchSize,
        long flushIntervalMs
) {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 500;

    public BatchProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        if (flushIntervalMs <= 0) {
            flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;
        }
    }
}
