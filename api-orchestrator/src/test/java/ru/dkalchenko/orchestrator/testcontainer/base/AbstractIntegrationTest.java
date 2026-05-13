package ru.dkalchenko.orchestrator.testcontainer.base;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.dkalchenko.orchestrator.testcontainer.containers.DeviceServiceMockContainer;
import ru.dkalchenko.orchestrator.testcontainer.containers.EventServiceMockContainer;
import ru.dkalchenko.orchestrator.testcontainer.containers.KeycloakTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	protected static final int ROUTER_PORT = 19090;

	@BeforeAll
	static void startContainers() {
		KeycloakTestContainer.INSTANCE.start();
		DeviceServiceMockContainer.INSTANCE.start();
		EventServiceMockContainer.INSTANCE.start();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				() -> "http://localhost:" + KeycloakTestContainer.INSTANCE.getMappedPort(8080) + "/realms/master");
		registry.add("clients.device-service.url",
				() -> "http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080));
		registry.add("clients.event-service.url",
				() -> "http://localhost:" + EventServiceMockContainer.INSTANCE.getMappedPort(8080));
		registry.add("clients.router-management.host", () -> "localhost");
		registry.add("clients.router-management.port", () -> ROUTER_PORT);
	}
}
