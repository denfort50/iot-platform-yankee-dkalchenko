package ru.dkalchenko.device.testcontainer.base;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.device.testcontainer.containers.KafkaTestContainer;
import ru.dkalchenko.device.testcontainer.containers.PostgresShardTestContainers;
import ru.dkalchenko.device.testcontainer.containers.RedisTestContainer;
import ru.dkalchenko.device.testcontainer.containers.SchemaRegistryTestContainer;

import java.util.List;

/**
 * Базовый класс интеграционных тестов: Kafka, Schema Registry, Redis, 3 шарда PostgreSQL.
 * Миграции выполняются по каждому шарду до старта контекста. В тестах безопасность отключена (permitAll).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.IntegrationTestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

	private static String jdbcUrlWithSearchPath(PostgreSQLContainer<?> c) {
		String url = c.getJdbcUrl();
		String params = "currentSchema=collector&options=-c%20search_path%3Dcollector,pg_catalog,public";
		return url.contains("?") ? url + "&" + params : url + "?" + params;
	}

	private static void migrateShards() {
		String user = PostgresShardTestContainers.SHARD0.getUsername();
		String password = PostgresShardTestContainers.SHARD0.getPassword();
		List<String> urls = List.of(
				PostgresShardTestContainers.SHARD0.getJdbcUrl(),
				PostgresShardTestContainers.SHARD1.getJdbcUrl(),
				PostgresShardTestContainers.SHARD2.getJdbcUrl()
		);
		for (String url : urls) {
			String urlForFlyway = url.contains("?") ? url + "&currentSchema=public" : url + "?currentSchema=public";
			HikariDataSource ds = new HikariDataSource();
			ds.setJdbcUrl(urlForFlyway);
			ds.setUsername(user);
			ds.setPassword(password);
			ds.setDriverClassName("org.postgresql.Driver");
			try {
				Flyway flyway = Flyway.configure()
						.dataSource(ds)
						.locations("classpath:db/migration")
						.schemas("public")
						.createSchemas(false)
						.baselineOnMigrate(true)
						.load();
				flyway.migrate();
			} finally {
				ds.close();
			}
		}
	}

	@BeforeAll
	static void startContainersAndMigrate() {
		KafkaTestContainer.INSTANCE.start();
		SchemaRegistryTestContainer.INSTANCE.start();
		RedisTestContainer.INSTANCE.start();
		PostgresShardTestContainers.SHARD0.start();
		PostgresShardTestContainers.SHARD1.start();
		PostgresShardTestContainers.SHARD2.start();
		migrateShards();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers",
				() -> "localhost:" + KafkaTestContainer.EXTERNAL_PORT);
		registry.add("spring.kafka.producer.properties.schema.registry.url",
				() -> "http://localhost:" + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081));
		registry.add("spring.kafka.properties.schema.registry.url",
				() -> "http://localhost:" + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081));

		registry.add("spring.data.redis.host", () -> "localhost");
		registry.add("spring.data.redis.port", () -> RedisTestContainer.INSTANCE.getMappedPort(6379));

		String url0 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD0);
		String url1 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD1);
		String url2 = jdbcUrlWithSearchPath(PostgresShardTestContainers.SHARD2);
		String user = PostgresShardTestContainers.SHARD0.getUsername();
		String password = PostgresShardTestContainers.SHARD0.getPassword();

		registry.add("POSTGRESQL_DS0_URL", () -> url0);
		registry.add("POSTGRESQL_DS0_REPLICA_URL", () -> url0);
		registry.add("POSTGRESQL_DS1_URL", () -> url1);
		registry.add("POSTGRESQL_DS1_REPLICA_URL", () -> url1);
		registry.add("POSTGRESQL_DS2_URL", () -> url2);
		registry.add("POSTGRESQL_DS2_REPLICA_URL", () -> url2);
		registry.add("POSTGRESQL_DS0_USER", () -> user);
		registry.add("POSTGRESQL_DS0_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS0_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS0_REPLICA_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS1_USER", () -> user);
		registry.add("POSTGRESQL_DS1_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS1_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS1_REPLICA_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS2_USER", () -> user);
		registry.add("POSTGRESQL_DS2_PASSWORD", () -> password);
		registry.add("POSTGRESQL_DS2_REPLICA_USER", () -> user);
		registry.add("POSTGRESQL_DS2_REPLICA_PASSWORD", () -> password);
	}

	@Configuration
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
