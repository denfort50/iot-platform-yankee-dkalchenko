package ru.dkalchenko.event.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;
import ru.dkalchenko.event.testcontainer.config.TestContainersConfig;

@UtilityClass
public class ClickHouseTestContainer {

	private static final String IMAGE = "clickhouse/clickhouse-server:24.8.4";

	@SuppressWarnings("resource")
	public static final ClickHouseContainer INSTANCE = new ClickHouseContainer(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("clickhouse");
}
