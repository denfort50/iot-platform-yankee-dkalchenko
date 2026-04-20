package ru.dkalchenko.event.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.event.properties.ClickHouseProperties;

import javax.sql.DataSource;

/**
 * Подключение к ClickHouse.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickHouseConfig {

	ClickHouseProperties clickHouseProperties;

	@Bean
	public DataSource clickHouseDataSource() {
		ClickHouseProperties.DataSource datasource = clickHouseProperties.datasource();
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(datasource.url());
		config.setUsername(datasource.username());
		config.setPassword(datasource.password());
		config.setDriverClassName(datasource.driverClassName());

		ClickHouseProperties.Pool pool = clickHouseProperties.pool();
		config.setMaximumPoolSize(pool.maximumPoolSize());
		config.setMinimumIdle(pool.minimumIdle());
		config.setConnectionTimeout(pool.connectionTimeout());
		config.setIdleTimeout(pool.idleTimeout());
		config.setMaxLifetime(pool.maxLifetime());
		config.setLeakDetectionThreshold(pool.leakDetectionThreshold());
		return new HikariDataSource(config);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource clickHouseDataSource) {
		return new JdbcTemplate(clickHouseDataSource);
	}
}
