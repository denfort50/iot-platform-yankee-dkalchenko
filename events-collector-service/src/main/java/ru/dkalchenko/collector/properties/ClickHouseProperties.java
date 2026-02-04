package ru.dkalchenko.collector.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства конфигурации для ClickHouse.
 * Автоматически подхватывает параметры из application.yaml с префиксом clickhouse.
 */
@ConfigurationProperties(prefix = "clickhouse")
public record ClickHouseProperties(
        DataSource datasource,
        Pool pool,
        int socketTimeout,
        int connectTimeout
) {
    /**
     * Настройки подключения к базе данных.
     */
    public record DataSource(
            String url,
            String username,
            String password,
            String driverClassName
    ) {
    }

    /**
     * Настройки пула соединений.
     */
    public record Pool(
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeout,
            long idleTimeout,
            long maxLifetime,
            long leakDetectionThreshold
    ) {
    }
}
