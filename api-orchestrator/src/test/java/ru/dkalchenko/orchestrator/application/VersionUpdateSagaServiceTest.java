package ru.dkalchenko.orchestrator.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.application.exception.UpstreamConflictException;
import ru.dkalchenko.orchestrator.application.model.DeviceView;
import ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest;
import ru.dkalchenko.orchestrator.application.model.MessageResult;
import ru.dkalchenko.orchestrator.application.model.TargetVersionCommand;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;
import ru.dkalchenko.orchestrator.domain.port.DeviceOutboundPort;
import ru.dkalchenko.orchestrator.domain.port.RouterCommandPort;
import ru.dkalchenko.orchestrator.domain.port.VersionUpdateIdempotencyPort;
import ru.dkalchenko.orchestrator.metrics.OrchestratorMetrics;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class VersionUpdateSagaServiceTest {

	static final String DEVICE_ID = "d1";
	static final String ROUTER_ID = "r1";

	@Mock
	DeviceOutboundPort devices;
	@Mock
	RouterCommandPort router;
	@Mock
	VersionUpdateIdempotencyPort idempotency;

	SimpleMeterRegistry meterRegistry;
	OrchestratorMetrics orchestratorMetrics;
	VersionUpdateSagaService saga;

	@BeforeEach
	void init() {
		meterRegistry = new SimpleMeterRegistry();
		orchestratorMetrics = new OrchestratorMetrics(meterRegistry);
		saga = new VersionUpdateSagaService(devices, router, idempotency, orchestratorMetrics);
	}

	@Test
	void whenSuccessThenNoRollback() {
		DeviceView before = new DeviceView(DEVICE_ID, "t", null, null, null, "1.0.0");
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.just(before));
		when(devices.setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class)))
				.thenReturn(Mono.just(new MessageResult("ok", DEVICE_ID)));
		when(router.sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0"))
				.thenReturn(Mono.just(List.of("cmd-1")));

		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, "idem-1");
		when(idempotency.find("d1::idem-1")).thenReturn(Mono.empty());
		when(idempotency.save(eq("d1::idem-1"), any(VersionUpdateResult.class))).thenReturn(Mono.empty());

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.assertNext(r -> {
					assertThat(r.compensated()).isFalse();
					assertThat(r.createdCommandIds()).containsExactly("cmd-1");
				})
				.verifyComplete();

		verify(devices, times(1)).setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class));
		verify(router).sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0");
	}

	@Test
	void whenRouterFailsThenCompensatesWhenPreviousTargetExists() {
		DeviceView before = new DeviceView(DEVICE_ID, "t", null, null, null, "1.0.0");
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.just(before));
		when(devices.setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class)))
				.thenAnswer(inv -> {
					TargetVersionCommand tv = inv.getArgument(1);
					if ("1.0.0".equals(tv.targetVersion()) && tv.idempotencyKey() != null
							&& tv.idempotencyKey().endsWith(":rollback")) {
						return Mono.just(new MessageResult("rollback", DEVICE_ID));
					}
					return Mono.just(new MessageResult("forward", DEVICE_ID));
				});
		when(router.sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0"))
				.thenReturn(Mono.error(new IllegalStateException("router down")));

		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, "idem-x");
		when(idempotency.find("d1::idem-x")).thenReturn(Mono.empty());
		when(idempotency.save(eq("d1::idem-x"), any(VersionUpdateResult.class))).thenReturn(Mono.empty());

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.assertNext(r -> {
					assertThat(r.compensated()).isTrue();
					assertThat(r.createdCommandIds()).isEmpty();
				})
				.verifyComplete();

		verify(devices, times(2)).setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class));
	}

	@Test
	void whenDeviceNotFoundThenReturnsError() {
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.empty());
		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, null);

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.expectError(DeviceNotFoundException.class)
				.verify();

		verify(router, never()).sendDeviceFirmwareUpdate(any(), any(), any());
		verify(devices, never()).setDeviceTargetVersion(any(), any());
	}

	@Test
	void whenRouterFailsAndWithoutPreviousTargetThenPropagatesOriginalError() {
		DeviceView before = new DeviceView(DEVICE_ID, "t", null, null, null, null);
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.just(before));
		when(devices.setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class)))
				.thenReturn(Mono.just(new MessageResult("ok", DEVICE_ID)));
		when(router.sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0"))
				.thenReturn(Mono.error(new IllegalStateException("router boom")));

		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, null);

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.expectError(IllegalStateException.class)
				.verify();

		verify(devices, times(1)).setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class));
	}

	@Test
	void whenDeviceNotFoundThenRecordsFailureMetric() {
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.empty());
		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, null);

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.expectError(DeviceNotFoundException.class)
				.verify();

		assertThat(meterRegistry
				.counter("orchestrator.version.update.total", "outcome", OrchestratorMetrics.OUTCOME_FAILURE)
				.count()).isEqualTo(1);
	}

	@Test
	void whenDeviceReturns409ThenUpstreamConflict() {
		DeviceView before = new DeviceView(DEVICE_ID, "t", null, null, null, null);
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.just(before));
		when(devices.setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class)))
				.thenReturn(Mono.error(new UpstreamConflictException("conflict")));

		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, null);

		StepVerifier.create(saga.updateDeviceVersion(DEVICE_ID, req))
				.expectError(UpstreamConflictException.class)
				.verify();

		verify(router, never()).sendDeviceFirmwareUpdate(any(), any(), any());
	}

	@Test
	void whenIdempotentSecondCallUsesCache() {
		DeviceView before = new DeviceView(DEVICE_ID, "t", null, null, null, "1.0.0");
		when(devices.getDevice(DEVICE_ID)).thenReturn(Mono.just(before));
		when(devices.setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class)))
				.thenReturn(Mono.just(new MessageResult("ok", DEVICE_ID)));
		when(router.sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0"))
				.thenReturn(Mono.just(Collections.singletonList("c1")));

		DeviceVersionUpdateRequest req = new DeviceVersionUpdateRequest("2.0.0", ROUTER_ID, "idem-only");
		when(idempotency.find("d1::idem-only"))
				.thenReturn(Mono.empty())
				.thenReturn(Mono.just(new VersionUpdateResult(false, "Version update orchestrated", List.of("c1"))));
		when(idempotency.save(eq("d1::idem-only"), any(VersionUpdateResult.class))).thenReturn(Mono.empty());

		saga.updateDeviceVersion(DEVICE_ID, req).block();
		saga.updateDeviceVersion(DEVICE_ID, req).block();

		verify(devices, times(1)).getDevice(DEVICE_ID);
		verify(devices, times(1)).setDeviceTargetVersion(eq(DEVICE_ID), any(TargetVersionCommand.class));
		verify(router, times(1)).sendDeviceFirmwareUpdate(ROUTER_ID, DEVICE_ID, "2.0.0");
		verify(idempotency, times(2)).find("d1::idem-only");
		verify(idempotency, times(1)).save(eq("d1::idem-only"), any(VersionUpdateResult.class));
		verifyNoMoreInteractions(devices, router, idempotency);
	}
}
