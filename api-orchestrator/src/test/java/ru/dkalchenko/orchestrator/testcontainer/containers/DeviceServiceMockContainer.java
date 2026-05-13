package ru.dkalchenko.orchestrator.testcontainer.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import ru.dkalchenko.orchestrator.testcontainer.config.TestContainersConfig;

import java.time.Duration;

public final class DeviceServiceMockContainer {

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>("wiremock/wiremock:3.13.1")
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("device-service-mock")
			.withExposedPorts(8080)
			.waitingFor(Wait.forHttp("/__admin").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));

	private DeviceServiceMockContainer() {
	}
}
