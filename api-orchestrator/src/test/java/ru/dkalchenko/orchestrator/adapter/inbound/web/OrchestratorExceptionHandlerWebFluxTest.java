package ru.dkalchenko.orchestrator.adapter.inbound.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.application.exception.RouterNotFoundException;
import ru.dkalchenko.orchestrator.application.exception.UpstreamConflictException;

public class OrchestratorExceptionHandlerWebFluxTest {

	@RestController
	static class ProbeController {

		@GetMapping("/test/conflict")
		Mono<ResponseEntity<Void>> conflict() {
			return Mono.error(new UpstreamConflictException("c1"));
		}

		@GetMapping("/test/bad-request")
		Mono<ResponseEntity<Void>> badRequest() {
			return Mono.error(new IllegalArgumentException("bad"));
		}

		@GetMapping("/test/device-not-found")
		Mono<ResponseEntity<Void>> deviceNotFound() {
			return Mono.error(new DeviceNotFoundException("d1"));
		}

		@GetMapping("/test/router-not-found")
		Mono<ResponseEntity<Void>> routerNotFound() {
			return Mono.error(new RouterNotFoundException("r1"));
		}
	}

	WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToController(new ProbeController())
				.controllerAdvice(new OrchestratorExceptionHandler())
				.build();
	}

	@Test
	void whenUpstreamConflictThenReturns409() {
		client.get().uri("/test/conflict")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.CONFLICT)
				.expectBody()
				.jsonPath("$.status").isEqualTo(409);
	}

	@Test
	void whenIllegalArgumentThenReturns400() {
		client.get().uri("/test/bad-request")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400);
	}

	@Test
	void whenDeviceNotFoundThenReturns404() {
		client.get().uri("/test/device-not-found")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void whenRouterNotFoundThenReturns404() {
		client.get().uri("/test/router-not-found")
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.status").isEqualTo(404);
	}
}
