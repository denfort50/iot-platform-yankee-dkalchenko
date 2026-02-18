package ru.dkalchenko.device.collector.config;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import javax.sql.DataSource;
import java.util.List;

/**
 * Запускает миграции Flyway на всех трёх шардах по очереди (прямое JDBC, без ShardingSphere),
 * чтобы избежать ошибок с pg_namespace при прохождении запросов через ShardingSphere.
 */
@Component
public class MultiShardFlywayMigrator implements Ordered {

    private static final List<String> URL_KEYS = List.of(
            "POSTGRESQL_DS0_URL",
            "POSTGRESQL_DS1_URL",
            "POSTGRESQL_DS2_URL"
    );

    private final Environment environment;

    public MultiShardFlywayMigrator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @PostConstruct
    public void migrateAllShards() {
        String user = environment.getRequiredProperty("POSTGRESQL_DS0_USER");
        String password = environment.getRequiredProperty("POSTGRESQL_DS0_PASSWORD");

        for (String urlKey : URL_KEYS) {
            String url = environment.getRequiredProperty(urlKey);
            // Для Flyway подключаемся без search_path, чтобы схема collector ещё не требовалась при установлении соединения
            String urlForFlyway = stripSearchPathParams(url);
            if (!urlForFlyway.contains("currentSchema=")) {
                String param = "currentSchema=collector";
                urlForFlyway = urlForFlyway.contains("?") ? urlForFlyway + "&" + param : urlForFlyway + "?" + param;
            }
            runFlyway(urlForFlyway, user, password);
        }
    }

    private void runFlyway(String jdbcUrl, String user, String password) {
        DataSource ds = createDataSource(jdbcUrl, user, password);
        try {
            // Запускаем в public, чтобы миграция V1 выполнила CREATE SCHEMA IF NOT EXISTS collector и таблицы в collector
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .schemas("public")
                    .createSchemas(false)
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
        } finally {
            if (ds instanceof HikariDataSource hikari) {
                hikari.close();
            }
        }
    }

    /**
     * Убирает из URL параметр options (search_path), чтобы первое подключение не требовало существования схемы.
     */
    private static String stripSearchPathParams(String url) {
        if (url == null || !url.contains("options=")) {
            return url;
        }
        String u = url.replaceAll("&options=[^&]+", "").replaceAll("\\?options=[^&]+&", "?");
        if (u.contains("?options=")) {
            u = u.replaceAll("\\?options=[^&]+", "");
        }
        return u;
    }

    private static DataSource createDataSource(String jdbcUrl, String user, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        return ds;
    }
}
