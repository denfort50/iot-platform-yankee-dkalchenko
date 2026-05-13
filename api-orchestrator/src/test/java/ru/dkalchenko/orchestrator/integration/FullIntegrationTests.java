package ru.dkalchenko.orchestrator.integration;

import api.v1.RouterManagement;
import api.v1.RouterManagementServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.dkalchenko.orchestrator.testcontainer.KeycloakInit;
import ru.dkalchenko.orchestrator.testcontainer.base.AbstractIntegrationTest;
import ru.dkalchenko.orchestrator.testcontainer.containers.DeviceServiceMockContainer;
import ru.dkalchenko.orchestrator.testcontainer.containers.EventServiceMockContainer;
import ru.dkalchenko.orchestrator.testcontainer.containers.KeycloakTestContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Полный интеграционный тест api-orchestrator:
 * реальный OAuth2 Resource Server (Keycloak в Testcontainers) и HTTP-заглушки upstream сервисов (WireMock в Testcontainers).
 */
class FullIntegrationTests extends AbstractIntegrationTest {

	private enum RouterTestMode {
		OK,
		SEND_ERROR_ROUTER_NOT_FOUND,
		POLL_ERROR_INVALID_ROUTER
	}

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static String accessToken;
	private static Server routerServer;
	private static volatile RouterTestMode routerTestMode = RouterTestMode.OK;

	@LocalServerPort
	int port;

	@Autowired
	WebTestClient webTestClient;

	@BeforeAll
	static void initKeycloakAndToken() {
		startRouterServer();
		String keycloakUrl = "http://localhost:" + KeycloakTestContainer.INSTANCE.getMappedPort(8080);
		KeycloakInit.run(keycloakUrl);
		accessToken = KeycloakInit.getTestUserToken(keycloakUrl);
	}

	@AfterAll
	static void stopRouterServer() throws InterruptedException {
		if (routerServer != null) {
			routerServer.shutdownNow().awaitTermination();
		}
	}

	@BeforeEach
	void resetWireMockMappings() {
		routerTestMode = RouterTestMode.OK;
		resetMappings("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080));
		resetMappings("http://localhost:" + EventServiceMockContainer.INSTANCE.getMappedPort(8080));
	}

	@Test
	void whenRequestWithoutTokenThenReturns401() {
		webTestClient.get()
				.uri("http://localhost:" + port + "/devices/" + UUID.randomUUID())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void whenGetDeviceAndNotFoundThenReturns404() {
		String deviceId = "device-" + UUID.randomUUID();
		stubDeviceGet(deviceId, 404, "{\"title\":\"Not Found\"}");

		webTestClient.get()
				.uri("http://localhost:" + port + "/devices/" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void whenGetDeviceFoundThenReturns200() {
		String deviceId = "device-" + UUID.randomUUID();
		String body = """
				{"deviceId":"%s","deviceType":"sensor","createdAt":1,"meta":"{}","updatedAt":"2020-01-01T00:00:00Z","targetSoftwareVersion":"1.0.0"}
				""".formatted(deviceId).replace("\n", "").trim();
		stubDeviceGet(deviceId, 200, body);

		webTestClient.get()
				.uri("http://localhost:" + port + "/devices/" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.deviceId").isEqualTo(deviceId)
				.jsonPath("$.deviceType").isEqualTo("sensor");
	}

	@Test
	void whenCreateUpdateDeleteDeviceThenAccepted() {
		stubDevicePost(202, "{\"message\":\"ok\",\"deviceId\":\"new-dev\"}");
		stubDevicePut("dev-put", 202, "{\"message\":\"ok\",\"deviceId\":\"dev-put\"}");
		stubDeviceDelete("dev-del", 202, "{\"message\":\"ok\",\"deviceId\":\"dev-del\"}");

		webTestClient.post()
				.uri("http://localhost:" + port + "/devices")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("{\"deviceType\":\"sensor\",\"meta\":\"{}\"}")
				.exchange()
				.expectStatus().isAccepted()
				.expectBody()
				.jsonPath("$.deviceId").isEqualTo("new-dev");

		webTestClient.put()
				.uri("http://localhost:" + port + "/devices/dev-put")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("{\"deviceType\":\"gw\",\"meta\":\"{}\"}")
				.exchange()
				.expectStatus().isAccepted();

		webTestClient.delete()
				.uri("http://localhost:" + port + "/devices/dev-del")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isAccepted();
	}

	@Test
	void whenUpdateTargetVersionThenReturns200() {
		String deviceId = "device-" + UUID.randomUUID();
		String deviceJson = """
				{"deviceId":"%s","deviceType":"sensor","createdAt":1,"meta":"{}","updatedAt":"2020-01-01T00:00:00Z","targetSoftwareVersion":"1.0.0"}
				""".formatted(deviceId).replace("\n", "").trim();
		stubDeviceGet(deviceId, 200, deviceJson);
		stubDevicePutVersion(deviceId, 202, "{\"message\":\"ok\",\"deviceId\":\"" + deviceId + "\"}");

		webTestClient.put()
				.uri("http://localhost:" + port + "/devices/" + deviceId + "/version")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{"targetVersion":"2.0.0","routerId":"router-1","idempotencyKey":"idem-int"}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.compensated").isEqualTo(false)
				.jsonPath("$.createdCommandIds[0]").isEqualTo("cmd-router-1");
	}

	@Test
	void whenUpdateTargetVersionAndDeviceNotFoundThen404() {
		String deviceId = "device-" + UUID.randomUUID();
		stubDeviceGet(deviceId, 404, "{\"title\":\"Not Found\"}");

		webTestClient.put()
				.uri("http://localhost:" + port + "/devices/" + deviceId + "/version")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("{\"targetVersion\":\"2.0.0\",\"routerId\":\"router-1\"}")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void whenUpdateTargetVersionAndUpstreamConflictThen409() {
		String deviceId = "device-" + UUID.randomUUID();
		String deviceJson = """
				{"deviceId":"%s","deviceType":"sensor","createdAt":1,"meta":"{}","updatedAt":"2020-01-01T00:00:00Z","targetSoftwareVersion":"1.0.0"}
				""".formatted(deviceId).replace("\n", "").trim();
		stubDeviceGet(deviceId, 200, deviceJson);
		stubDevicePutVersion(deviceId, 409, "{\"title\":\"Conflict\",\"detail\":\"c\"}");

		webTestClient.put()
				.uri("http://localhost:" + port + "/devices/" + deviceId + "/version")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("{\"targetVersion\":\"9.9.9\",\"routerId\":\"router-1\"}")
				.exchange()
				.expectStatus().isEqualTo(409);
	}

	@Test
	void whenGetEventByIdAndNotFoundThenReturns404() {
		String eventId = "event-" + UUID.randomUUID();
		String deviceId = "device-" + UUID.randomUUID();
		stubEventGetById(eventId, deviceId, 404, "{\"title\":\"Not Found\"}");

		webTestClient.get()
				.uri("http://localhost:" + port + "/events/" + eventId + "?deviceId=" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void whenGetEventByIdFoundThenReturns200() {
		String eventId = "evt-1";
		String deviceId = "dev-1";
		String body = "{\"eventId\":\"" + eventId + "\",\"deviceId\":\"" + deviceId
				+ "\",\"timestamp\":100,\"type\":\"T\",\"payload\":\"{}\"}";
		stubEventGetById(eventId, deviceId, 200, body);

		webTestClient.get()
				.uri("http://localhost:" + port + "/events/" + eventId + "?deviceId=" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.eventId").isEqualTo(eventId)
				.jsonPath("$.type").isEqualTo("T");
	}

	@Test
	void whenListEventsThenReturns200() {
		String deviceId = "dev-list-" + UUID.randomUUID();
		stubEventList(deviceId, 200, "{\"events\":[],\"page\":0,\"size\":100,\"total\":0}");

		webTestClient.get()
				.uri("http://localhost:" + port + "/events?deviceId=" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.total").isEqualTo(0);
	}

	@Test
	void whenListEventsAndDeviceMissingThen404() {
		String deviceId = "dev-missing-" + UUID.randomUUID();
		stubEventList(deviceId, 404, "{\"title\":\"Not Found\"}");

		webTestClient.get()
				.uri("http://localhost:" + port + "/events?deviceId=" + deviceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void whenSendCommandThenReturns200AndCommandIds() {
		webTestClient.post()
				.uri("http://localhost:" + port + "/commands")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{
						  "routerId": "router-1",
						  "commandType": "PING",
						  "payload": "{}"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.commandIds[0]").isEqualTo("cmd-router-1");
	}

	@Test
	void whenSendCommandAndGrpcRouterNotFoundThen404() {
		routerTestMode = RouterTestMode.SEND_ERROR_ROUTER_NOT_FOUND;
		webTestClient.post()
				.uri("http://localhost:" + port + "/commands")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{"routerId":"any","commandType":"PING","payload":"{}"}
						""")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void whenRegisterRouterThenReturns200AndRouterId() {
		webTestClient.post()
				.uri("http://localhost:" + port + "/routers/register")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{
						  "inventoryNumber": "inv-100500",
						  "name": "Main router",
						  "model": "RTX-1",
						  "location": "HQ-1"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.routerId").isEqualTo("router-inv-100500");
	}

	@Test
	void whenPollCommandsThenReturns200AndCommands() {
		webTestClient.post()
				.uri("http://localhost:" + port + "/commands/poll")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{
						  "routerId": "router-1"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.commands[0].commandId").isEqualTo("cmd-1")
				.jsonPath("$.commands[0].commandType").isEqualTo("PING")
				.jsonPath("$.commands[0].status").isEqualTo("PENDING");
	}

	@Test
	void whenPollCommandsAndGrpcInvalidRouterThen400() {
		routerTestMode = RouterTestMode.POLL_ERROR_INVALID_ROUTER;
		webTestClient.post()
				.uri("http://localhost:" + port + "/commands/poll")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("{\"routerId\":\"router-1\"}")
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void whenAckCommandThenReturns200AndSuccessTrue() {
		webTestClient.post()
				.uri("http://localhost:" + port + "/commands/ack")
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.bodyValue("""
						{
						  "routerId": "router-1",
						  "commandId": "cmd-1"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.success").isEqualTo(true);
	}

	private static void stubDeviceGet(String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": {
				    "method": "GET",
				    "urlPath": "/devices/%s"
				  },
				  "response": {
				    "status": %d,
				    "headers": {
				      "Content-Type": "application/json"
				    },
				    "body": %s
				  }
				}
				""".formatted(deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubDevicePost(int status, String responseBody) {
		String mapping = """
				{
				  "request": { "method": "POST", "urlPath": "/devices" },
				  "response": {
				    "status": %d,
				    "headers": { "Content-Type": "application/json" },
				    "body": %s
				  }
				}
				""".formatted(status, toJsonString(responseBody));
		registerMapping("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubDevicePut(String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": { "method": "PUT", "urlPath": "/devices/%s" },
				  "response": {
				    "status": %d,
				    "headers": { "Content-Type": "application/json" },
				    "body": %s
				  }
				}
				""".formatted(deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubDeviceDelete(String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": { "method": "DELETE", "urlPath": "/devices/%s" },
				  "response": {
				    "status": %d,
				    "headers": { "Content-Type": "application/json" },
				    "body": %s
				  }
				}
				""".formatted(deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubDevicePutVersion(String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": { "method": "PUT", "urlPath": "/devices/%s/version" },
				  "response": {
				    "status": %d,
				    "headers": { "Content-Type": "application/json" },
				    "body": %s
				  }
				}
				""".formatted(deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + DeviceServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubEventGetById(String eventId, String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": {
				    "method": "GET",
				    "urlPath": "/api/v1/events/%s",
				    "queryParameters": {
				      "deviceId": { "equalTo": "%s" }
				    }
				  },
				  "response": {
				    "status": %d,
				    "headers": {
				      "Content-Type": "application/json"
				    },
				    "body": %s
				  }
				}
				""".formatted(eventId, deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + EventServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void stubEventList(String deviceId, int status, String responseBody) {
		String mapping = """
				{
				  "request": {
				    "method": "GET",
				    "urlPath": "/api/v1/events",
				    "queryParameters": {
				      "deviceId": { "equalTo": "%s" }
				    }
				  },
				  "response": {
				    "status": %d,
				    "headers": { "Content-Type": "application/json" },
				    "body": %s
				  }
				}
				""".formatted(deviceId, status, toJsonString(responseBody));
		registerMapping("http://localhost:" + EventServiceMockContainer.INSTANCE.getMappedPort(8080), mapping);
	}

	private static void startRouterServer() {
		try {
			routerServer = ServerBuilder.forPort(ROUTER_PORT)
					.addService(new RouterManagementServiceGrpc.RouterManagementServiceImplBase() {
						@Override
						public void registerRouter(RouterManagement.RegisterRouterRequest request,
								StreamObserver<RouterManagement.RegisterRouterResponse> responseObserver) {
							RouterManagement.RegisterRouterResponse response = RouterManagement.RegisterRouterResponse.newBuilder()
									.setRouterId("router-" + request.getInventoryNumber())
									.build();
							responseObserver.onNext(response);
							responseObserver.onCompleted();
						}

						@Override
						public void sendCommand(RouterManagement.SendCommandRequest request,
								StreamObserver<RouterManagement.SendCommandResponse> responseObserver) {
							if (routerTestMode == RouterTestMode.SEND_ERROR_ROUTER_NOT_FOUND) {
								responseObserver.onNext(RouterManagement.SendCommandResponse.newBuilder()
										.setError("router not found")
										.build());
								responseObserver.onCompleted();
								return;
							}
							RouterManagement.SendCommandResponse response = RouterManagement.SendCommandResponse.newBuilder()
									.addCommandIds("cmd-" + request.getRouterId())
									.build();
							responseObserver.onNext(response);
							responseObserver.onCompleted();
						}

						@Override
						public void commandsPoll(RouterManagement.CommandsPollRequest request,
								StreamObserver<RouterManagement.CommandsPollResponse> responseObserver) {
							if (routerTestMode == RouterTestMode.POLL_ERROR_INVALID_ROUTER) {
								responseObserver.onNext(RouterManagement.CommandsPollResponse.newBuilder()
										.setError("invalid router_id")
										.build());
								responseObserver.onCompleted();
								return;
							}
							RouterManagement.CommandsPollResponse response = RouterManagement.CommandsPollResponse.newBuilder()
									.addCommands(RouterManagement.CommandMessage.newBuilder()
											.setCommandId("cmd-1")
											.setCommandType("PING")
											.setPayload("{}")
											.setStatus("PENDING")
											.build())
									.build();
							responseObserver.onNext(response);
							responseObserver.onCompleted();
						}

						@Override
						public void commandsAck(RouterManagement.CommandsAckRequest request,
								StreamObserver<RouterManagement.CommandsAckResponse> responseObserver) {
							RouterManagement.CommandsAckResponse response = RouterManagement.CommandsAckResponse.newBuilder()
									.setSuccess(true)
									.build();
							responseObserver.onNext(response);
							responseObserver.onCompleted();
						}
					})
					.build()
					.start();
		} catch (Exception e) {
			throw new IllegalStateException("Cannot start gRPC router test server", e);
		}
	}

	private static void resetMappings(String baseUrl) {
		send(baseUrl + "/__admin/mappings", "DELETE", null);
	}

	private static void registerMapping(String baseUrl, String mapping) {
		send(baseUrl + "/__admin/mappings", "POST", mapping);
	}

	private static void send(String url, String method, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(10));
		if ("DELETE".equals(method)) {
			builder.DELETE();
		} else {
			builder.header("Content-Type", "application/json");
			builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
		}
		try {
			HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalStateException("WireMock admin call failed: " + method + " " + url + " -> "
						+ response.statusCode() + ", body=" + response.body());
			}
		} catch (Exception e) {
			throw new IllegalStateException("WireMock admin call failed: " + method + " " + url, e);
		}
	}

	private static String toJsonString(String raw) {
		String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
		return "\"" + escaped + "\"";
	}
}
