package ru.dkalchenko.collector.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import ru.dkalchenko.collector.testcontainer.config.TestContainersConfig;

@UtilityClass
public class RedisTestContainer {

    public static final GenericContainer<?> INSTANCE = new GenericContainer<>("redis:8.4.0-alpine")
                .withNetwork(TestContainersConfig.NETWORK)
                .withNetworkAliases("redis")
                .withExposedPorts(6379);

}
