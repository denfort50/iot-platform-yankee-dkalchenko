package ru.dkalchenko.collector.testcontainer.base;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.collector.testcontainer.containers.ClickhouseTestContainer;
import ru.dkalchenko.collector.testcontainer.containers.KafkaTestContainer;
import ru.dkalchenko.collector.testcontainer.containers.RedisTestContainer;
import ru.dkalchenko.collector.testcontainer.containers.SchemaRegistryTestContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @BeforeAll
    static void startContainers() {
        KafkaTestContainer.INSTANCE.start();
        SchemaRegistryTestContainer.INSTANCE.start();
        ClickhouseTestContainer.INSTANCE.start();
        RedisTestContainer.INSTANCE.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> "localhost:" + KafkaTestContainer.EXTERNAL_PORT
        );

        registry.add(
                "spring.kafka.properties.schema.registry.url",
                () -> "http://localhost:"
                        + SchemaRegistryTestContainer.INSTANCE.getMappedPort(8081)
        );

        registry.add(
                "spring.data.redis.host",
                RedisTestContainer.INSTANCE::getHost
        );
        registry.add(
                "spring.data.redis.port",
                () -> RedisTestContainer.INSTANCE.getMappedPort(6379)
        );

        registry.add(
                "clickhouse.datasource.url",
                () -> "jdbc:clickhouse://"
                        + ClickhouseTestContainer.INSTANCE.getHost()
                        + ":"
                        + ClickhouseTestContainer.INSTANCE.getMappedPort(8123)
                        + "/test"
        );
    }
}
