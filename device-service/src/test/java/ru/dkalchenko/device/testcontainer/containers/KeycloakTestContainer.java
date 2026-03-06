package ru.dkalchenko.device.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import ru.dkalchenko.device.testcontainer.config.TestContainersConfig;

import java.time.Duration;

/**
 * Keycloak 26.2 — тот же образ, что в infrastructure/docker-compose.yaml.
 * Режим start-dev (встроенная БД). В start-dev management-порт 9000 может подниматься
 * с задержкой, поэтому ждём по логу готовности приложения.
 */
@UtilityClass
public class KeycloakTestContainer {

	private static final String IMAGE = "quay.io/keycloak/keycloak:26.2";

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>(DockerImageName.parse(IMAGE))
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("keycloak")
			.withExposedPorts(8080, 9000)
			.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
			.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
			.withCommand("start-dev")
			.waitingFor(Wait.forLogMessage(".*Listening on:.*8080.*", 1).withStartupTimeout(Duration.ofSeconds(120)));
}
