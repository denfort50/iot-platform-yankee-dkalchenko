package ru.dkalchenko.event.testcontainer.base;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.event.testcontainer.containers.ClickHouseTestContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.IntegrationTestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

	@BeforeAll
	static void startContainerAndInitSchema() throws SQLException {
		ClickHouseTestContainer.INSTANCE.start();
		try (
				Connection connection = DriverManager.getConnection(
						ClickHouseTestContainer.INSTANCE.getJdbcUrl(),
						ClickHouseTestContainer.INSTANCE.getUsername(),
						ClickHouseTestContainer.INSTANCE.getPassword()
				);
				Statement statement = connection.createStatement()
		) {
			statement.execute("CREATE DATABASE IF NOT EXISTS collector");
			statement.execute("""
					CREATE TABLE IF NOT EXISTS collector.device_events (
						event_id String,
						device_id String,
						timestamp_ms Int64,
						type String,
						payload String
					) ENGINE = MergeTree()
					ORDER BY (device_id, timestamp_ms, event_id)
					""");
		}
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("clickhouse.datasource.url", ClickHouseTestContainer.INSTANCE::getJdbcUrl);
		registry.add("clickhouse.datasource.username", ClickHouseTestContainer.INSTANCE::getUsername);
		registry.add("clickhouse.datasource.password", ClickHouseTestContainer.INSTANCE::getPassword);
		registry.add("clickhouse.datasource.driver-class-name", () -> "com.clickhouse.jdbc.ClickHouseDriver");
	}

	@Configuration
	@Profile("!keycloak")
	static class IntegrationTestSecurityConfig {
		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			http
					.securityMatcher(request -> true)
					.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
					.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable);
			return http.build();
		}
	}
}
