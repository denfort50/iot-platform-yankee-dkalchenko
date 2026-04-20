package ru.dkalchenko.event.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.event.testcontainer.base.AbstractIntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventApiIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clearData() {
		jdbcTemplate.execute("TRUNCATE TABLE collector.device_events");
	}

	@Test
	void whenListEventsWithFiltersThenReturnsPagedAndSortedEvents() throws Exception {
		insertEvent("e-1", "dev-1", 1_000L, "temperature", "{\"value\":1}");
		insertEvent("e-2", "dev-1", 2_000L, "temperature", "{\"value\":2}");
		insertEvent("e-3", "dev-1", 3_000L, "humidity", "{\"value\":3}");
		insertEvent("e-4", "dev-2", 4_000L, "temperature", "{\"value\":4}");

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/events?deviceId=dev-1&fromTimestamp=500&toTimestamp=3000&type=temperature&page=0&size=10",
				String.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("total").asLong()).isEqualTo(2L);
		assertThat(root.path("events")).hasSize(2);
		assertThat(root.path("events").get(0).path("eventId").asText()).isEqualTo("e-2");
		assertThat(root.path("events").get(1).path("eventId").asText()).isEqualTo("e-1");
	}

	@Test
	void whenGetEventByIdAndExistsThenReturnsEvent() throws Exception {
		insertEvent("evt-1", "dev-99", 8_000L, "battery", "{\"level\":90}");

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/events/evt-1?deviceId=dev-99",
				String.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("eventId").asText()).isEqualTo("evt-1");
		assertThat(root.path("deviceId").asText()).isEqualTo("dev-99");
		assertThat(root.path("type").asText()).isEqualTo("battery");
	}

	@Test
	void whenGetEventByIdAndMissingThenReturns404WithProblemDetail() {
		insertEvent("evt-1", "dev-99", 8_000L, "battery", "{\"level\":90}");

		ResponseEntity<Map> response = restTemplate.getForEntity(
				"/api/v1/events/missing?deviceId=dev-99",
				Map.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("detail").toString()).contains("Event not found");
	}

	@Test
	void whenListEventsAndDeviceMissingThenReturns404() {
		ResponseEntity<Map> response = restTemplate.getForEntity(
				"/api/v1/events?deviceId=unknown",
				Map.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("detail").toString()).contains("Device not found");
	}

	@Test
	void whenListEventsAndInvalidRangeThenReturns400() {
		insertEvent("e-1", "dev-1", 1_000L, "temperature", "{\"value\":1}");

		ResponseEntity<Map> response = restTemplate.getForEntity(
				"/api/v1/events?deviceId=dev-1&fromTimestamp=5000&toTimestamp=1000",
				Map.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("detail").toString()).contains("fromTimestamp must be less than or equal");
	}

	private void insertEvent(String eventId, String deviceId, long timestamp, String type, String payload) {
		jdbcTemplate.update(
				"INSERT INTO collector.device_events (event_id, device_id, timestamp_ms, type, payload) VALUES (?, ?, ?, ?, ?)",
				eventId, deviceId, timestamp, type, payload
		);
	}
}
