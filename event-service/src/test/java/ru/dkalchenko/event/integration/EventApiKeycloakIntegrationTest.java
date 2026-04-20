package ru.dkalchenko.event.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.dkalchenko.event.testcontainer.KeycloakInit;
import ru.dkalchenko.event.testcontainer.base.AbstractIntegrationTest;
import ru.dkalchenko.event.testcontainer.containers.KeycloakTestContainer;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "keycloak"})
class EventApiKeycloakIntegrationTest extends AbstractIntegrationTest {

	private static String accessToken;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TestRestTemplate restTemplate;

	@BeforeAll
	static void startKeycloakAndObtainToken() {
		KeycloakTestContainer.INSTANCE.start();
		String keycloakUrl = "http://localhost:" + KeycloakTestContainer.INSTANCE.getMappedPort(8080);
		KeycloakInit.run(keycloakUrl);
		accessToken = KeycloakInit.getTestUserToken(keycloakUrl);
	}

	@DynamicPropertySource
	static void keycloakProperties(DynamicPropertyRegistry registry) {
		String issuerUri = "http://localhost:" + KeycloakTestContainer.INSTANCE.getMappedPort(8080) + "/realms/master";
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
	}

	@BeforeEach
	void clearData() {
		jdbcTemplate.execute("TRUNCATE TABLE collector.device_events");
	}

	@Test
	void whenRequestWithoutTokenThenReturns401() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/events?deviceId=dev-1", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void whenRequestWithTokenThenReturns200() {
		insertEvent("evt-1", "dev-1", 1_000L, "temperature", "{\"value\":5}");
		var request = RequestEntity
				.get(URI.create("/api/v1/events?deviceId=dev-1"))
				.headers(authHeaders())
				.build();

		ResponseEntity<String> response = restTemplate.exchange(request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"eventId\":\"evt-1\"");
	}

	private HttpHeaders authHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return headers;
	}

	private void insertEvent(String eventId, String deviceId, long timestamp, String type, String payload) {
		jdbcTemplate.update(
				"INSERT INTO collector.device_events (event_id, device_id, timestamp_ms, type, payload) VALUES (?, ?, ?, ?, ?)",
				eventId, deviceId, timestamp, type, payload
		);
	}
}
