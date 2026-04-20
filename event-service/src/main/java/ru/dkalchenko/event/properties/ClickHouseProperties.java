package ru.dkalchenko.event.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры подключения к ClickHouse.
 */
@ConfigurationProperties(prefix = "clickhouse")
public record ClickHouseProperties(
		DataSource datasource,
		Pool pool,
		int socketTimeout,
		int connectTimeout
) {
	public record DataSource(
			String url,
			String username,
			String password,
			String driverClassName
	) {
	}

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
