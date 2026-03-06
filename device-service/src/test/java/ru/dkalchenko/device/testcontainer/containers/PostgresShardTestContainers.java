package ru.dkalchenko.device.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import ru.dkalchenko.device.testcontainer.config.TestContainersConfig;

@UtilityClass
public class PostgresShardTestContainers {

	private static final String IMAGE = "postgres:17-alpine";
	private static final String USER = "admin";
	private static final String PASSWORD = "admin";

	@SuppressWarnings("resource")
	public static final PostgreSQLContainer<?> SHARD0 = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("postgres-shard0")
			.withDatabaseName("iot_platform_shard0")
			.withUsername(USER)
			.withPassword(PASSWORD);

	@SuppressWarnings("resource")
	public static final PostgreSQLContainer<?> SHARD1 = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("postgres-shard1")
			.withDatabaseName("iot_platform_shard1")
			.withUsername(USER)
			.withPassword(PASSWORD);

	@SuppressWarnings("resource")
	public static final PostgreSQLContainer<?> SHARD2 = new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("postgres-shard2")
			.withDatabaseName("iot_platform_shard2")
			.withUsername(USER)
			.withPassword(PASSWORD);
}
