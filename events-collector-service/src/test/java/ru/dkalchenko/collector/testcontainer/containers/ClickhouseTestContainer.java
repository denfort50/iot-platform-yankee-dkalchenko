package ru.dkalchenko.collector.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import ru.dkalchenko.collector.testcontainer.config.TestContainersConfig;

@UtilityClass
public class ClickhouseTestContainer {

    public static final GenericContainer<?> INSTANCE = new GenericContainer<>("clickhouse/clickhouse-server:25.12.4")
            .withNetwork(TestContainersConfig.NETWORK)
            .withNetworkAliases("clickhouse")
            .withExposedPorts(8123, 9363)
            .withEnv("CLICKHOUSE_DB", "test")
            .withEnv("CLICKHOUSE_USER", "test")
            .withEnv("CLICKHOUSE_PASSWORD", "test")
            .waitingFor(Wait.forHttp("/ping").forPort(8123).forStatusCode(200));

}
