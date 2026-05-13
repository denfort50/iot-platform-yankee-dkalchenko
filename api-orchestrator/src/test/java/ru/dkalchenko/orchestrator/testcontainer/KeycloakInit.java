package ru.dkalchenko.orchestrator.testcontainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class KeycloakInit {

	private static final String ADMIN_USER = "admin";
	private static final String ADMIN_PASSWORD = "admin";
	private static final String TEST_USERNAME = "orchestrator-api-user";
	private static final String TEST_PASSWORD = "orchestrator-api-password";
	private static final String REALM = "master";
	private static final String ADMIN_CLI = "admin-cli";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private KeycloakInit() {
	}

	public static void run(String keycloakBaseUrl) {
		String base = keycloakBaseUrl.endsWith("/") ? keycloakBaseUrl : keycloakBaseUrl + "/";
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		String token = getAdminToken(client, base);
		createUser(client, base, token);
		enableDirectAccessGrants(client, base, token);
	}

	public static String getTestUserToken(String keycloakBaseUrl) {
		String base = keycloakBaseUrl.endsWith("/") ? keycloakBaseUrl : keycloakBaseUrl + "/";
		String form = "grant_type=password&client_id=" + ADMIN_CLI
				+ "&username=" + TEST_USERNAME
				+ "&password=" + TEST_PASSWORD;
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(base + "realms/" + REALM + "/protocol/openid-connect/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Failed to get test user token: " + response.statusCode() + " " + response.body());
			}
			JsonNode node = MAPPER.readTree(response.body());
			return node.get("access_token").asText();
		} catch (Exception e) {
			throw new IllegalStateException("Keycloak get test user token failed", e);
		}
	}

	private static String getAdminToken(HttpClient client, String base) {
		String form = "grant_type=password&client_id=" + ADMIN_CLI
				+ "&username=" + ADMIN_USER
				+ "&password=" + ADMIN_PASSWORD;
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(base + "realms/" + REALM + "/protocol/openid-connect/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Failed to get admin token: " + response.statusCode() + " " + response.body());
			}
			JsonNode node = MAPPER.readTree(response.body());
			return node.get("access_token").asText();
		} catch (Exception e) {
			throw new IllegalStateException("Keycloak get admin token failed", e);
		}
	}

	private static void createUser(HttpClient client, String base, String token) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("username", TEST_USERNAME);
		body.put("enabled", true);
		ArrayNode credentials = body.putArray("credentials");
		ObjectNode cred = credentials.addObject();
		cred.put("type", "password");
		cred.put("value", TEST_PASSWORD);
		cred.put("temporary", false);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(base + "admin/realms/" + REALM + "/users"))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token)
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 409) {
				return;
			}
			if (response.statusCode() != 201 && response.statusCode() != 204) {
				throw new IllegalStateException("Create user failed: " + response.statusCode() + " " + response.body());
			}
		} catch (Exception e) {
			throw new IllegalStateException("Keycloak create user failed", e);
		}
	}

	private static void enableDirectAccessGrants(HttpClient client, String base, String token) {
		HttpRequest listRequest = HttpRequest.newBuilder()
				.uri(URI.create(base + "admin/realms/" + REALM + "/clients?clientId=" + ADMIN_CLI))
				.header("Authorization", "Bearer " + token)
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		try {
			HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (listResponse.statusCode() != 200) {
				throw new IllegalStateException("Get clients failed: " + listResponse.statusCode());
			}
			JsonNode clients = MAPPER.readTree(listResponse.body());
			if (!clients.isArray() || clients.isEmpty()) {
				throw new IllegalStateException("admin-cli client not found");
			}
			JsonNode clientNode = clients.get(0);
			if (clientNode.get("directAccessGrantsEnabled").asBoolean()) {
				return;
			}
			String clientId = clientNode.get("id").asText();
			ObjectNode updated = (ObjectNode) clientNode.deepCopy();
			updated.put("directAccessGrantsEnabled", true);

			HttpRequest putRequest = HttpRequest.newBuilder()
					.uri(URI.create(base + "admin/realms/" + REALM + "/clients/" + clientId))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + token)
					.timeout(Duration.ofSeconds(10))
					.PUT(HttpRequest.BodyPublishers.ofString(updated.toString()))
					.build();
			HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (putResponse.statusCode() != 200 && putResponse.statusCode() != 204) {
				throw new IllegalStateException("Enable direct access failed: " + putResponse.statusCode());
			}
		} catch (Exception e) {
			throw new IllegalStateException("Keycloak enable direct access failed", e);
		}
	}
}
