package ru.dkalchenko.orchestrator.testcontainer.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import ru.dkalchenko.orchestrator.testcontainer.config.TestContainersConfig;

import java.time.Duration;

public final class KeycloakTestContainer {

	private static final String IMAGE = "quay.io/keycloak/keycloak:26.2";

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("keycloak")
			.withExposedPorts(8080, 9000)
			.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
			.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
			.withCommand("start-dev")
			.waitingFor(Wait.forLogMessage(".*Listening on:.*8080.*", 1).withStartupTimeout(Duration.ofSeconds(120)));

	private KeycloakTestContainer() {
	}
}
