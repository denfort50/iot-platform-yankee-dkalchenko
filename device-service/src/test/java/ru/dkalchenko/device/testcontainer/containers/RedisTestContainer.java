package ru.dkalchenko.device.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import ru.dkalchenko.device.testcontainer.config.TestContainersConfig;

@UtilityClass
public class RedisTestContainer {

	private static final String IMAGE = "redis:7-alpine";

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("redis")
			.withExposedPorts(6379);
}
