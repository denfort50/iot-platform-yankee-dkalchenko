package ru.dkalchenko.orchestrator.adapter.outbound.device;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import ru.dkalchenko.device.api.model.CreateResponse;
import ru.dkalchenko.device.api.model.Device;
import ru.dkalchenko.device.api.model.DeviceRequest;
import ru.dkalchenko.device.api.model.MessageResponse;
import ru.dkalchenko.orchestrator.adapter.outbound.feign.DeviceServiceFeignClient;
import ru.dkalchenko.orchestrator.application.exception.UpstreamConflictException;
import ru.dkalchenko.orchestrator.application.model.DeviceCommand;
import ru.dkalchenko.orchestrator.application.model.TargetVersionCommand;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientDeviceOutboundAdapterTest {

	@Mock
	DeviceServiceFeignClient client;

	ResilientDeviceOutboundAdapter adapter;

	@BeforeEach
	void setUp() {
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
		TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(
				TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(30)).build());
		adapter = new ResilientDeviceOutboundAdapter(client, cbRegistry, tlRegistry);
	}

	private static Context bearerContext() {
		return Context.of(ReactiveBearerContext.KEY, "test-jwt");
	}

	@Test
	void whenGetDeviceAndWhenUpstream404ThenReturnsEmpty() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(404);
		when(client.getDevice("missing")).thenThrow(ex);

		StepVerifier.create(adapter.getDevice("missing").contextWrite(bearerContext()))
				.verifyComplete();
	}

	@Test
	void whenGetDeviceAndWhenUpstream500ThenPropagates() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(500);
		when(client.getDevice("x")).thenThrow(ex);

		StepVerifier.create(adapter.getDevice("x").contextWrite(bearerContext()))
				.expectError(FeignException.class)
				.verify();
	}

	@Test
	void whenGetDeviceAndWhenOkThenMapsToView() {
		Device d = new Device()
				.deviceId("d1")
				.deviceType("sensor")
				.createdAt(1L)
				.meta("{}")
				.updatedAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"))
				.targetSoftwareVersion("1.0.0");
		when(client.getDevice("d1")).thenReturn(d);

		StepVerifier.create(adapter.getDevice("d1").contextWrite(bearerContext()))
				.assertNext(v -> {
					assertThat(v.deviceId()).isEqualTo("d1");
					assertThat(v.deviceType()).isEqualTo("sensor");
					assertThat(v.targetSoftwareVersion()).isEqualTo("1.0.0");
				})
				.verifyComplete();
	}

	@Test
	void whenCreateDeviceAndWhenOkThenMmapsResult() {
		when(client.createDevice(any(DeviceRequest.class)))
				.thenReturn(new CreateResponse().message("accepted").deviceId("new-1"));

		StepVerifier.create(adapter.createDevice(new DeviceCommand("sensor", "{}")).contextWrite(bearerContext()))
				.assertNext(r -> {
					assertThat(r.deviceId()).isEqualTo("new-1");
					assertThat(r.message()).isEqualTo("accepted");
				})
				.verifyComplete();
	}

	@Test
	void whenSetDeviceTargetVersionAndWhen409ThenMapsToUpstreamConflict() {
		FeignException ex = mock(FeignException.class);
		when(ex.status()).thenReturn(409);
		when(ex.getMessage()).thenReturn("conflict");
		when(client.setDeviceTargetVersion(eq("d1"), any())).thenThrow(ex);

		StepVerifier.create(adapter.setDeviceTargetVersion("d1", new TargetVersionCommand("2.0.0", "idem"))
						.contextWrite(bearerContext()))
				.expectError(UpstreamConflictException.class)
				.verify();
	}

	@Test
	void whenUpdateDeviceAndWhenOkThenReturnsMessage() {
		when(client.updateDevice(eq("d1"), any(DeviceRequest.class)))
				.thenReturn(new MessageResponse().message("queued").deviceId("d1"));

		StepVerifier.create(adapter.updateDevice("d1", new DeviceCommand("gw", "{}")).contextWrite(bearerContext()))
				.assertNext(m -> assertThat(m.message()).isEqualTo("queued"))
				.verifyComplete();
	}

	@Test
	void whenDeleteDeviceAndWhenOkThenReturnsMessage() {
		when(client.deleteDevice("d1")).thenReturn(new MessageResponse().message("deleted").deviceId("d1"));

		StepVerifier.create(adapter.deleteDevice("d1").contextWrite(bearerContext()))
				.assertNext(m -> assertThat(m.message()).isEqualTo("deleted"))
				.verifyComplete();
	}
}
