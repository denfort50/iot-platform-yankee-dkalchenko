package ru.dkalchenko.orchestrator.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.application.model.DeviceVersionUpdateRequest;
import ru.dkalchenko.orchestrator.application.model.TargetVersionCommand;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;
import ru.dkalchenko.orchestrator.domain.port.DeviceOutboundPort;
import ru.dkalchenko.orchestrator.domain.port.RouterCommandPort;
import ru.dkalchenko.orchestrator.domain.port.VersionUpdateIdempotencyPort;
import ru.dkalchenko.orchestrator.metrics.OrchestratorMetrics;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VersionUpdateSagaService {

	DeviceOutboundPort devices;
	RouterCommandPort router;
	VersionUpdateIdempotencyPort idempotency;
	OrchestratorMetrics metrics;

	public Mono<VersionUpdateResult> updateDeviceVersion(String deviceId, DeviceVersionUpdateRequest body) {
		String idem = body.idempotencyKey();
		if (idem != null && !idem.isBlank()) {
			return idempotency.find(cacheKey(deviceId, idem))
					.doOnNext(r -> {
						metrics.recordIdempotentReplay();
						log.info("Version update idempotent replay for deviceId={}, compensated={}",
								deviceId, r.compensated());
					})
					.switchIfEmpty(Mono.defer(() -> timedRunUpdate(deviceId, body, idem)));
		}
		return timedRunUpdate(deviceId, body, idem);
	}

	private Mono<VersionUpdateResult> timedRunUpdate(String deviceId, DeviceVersionUpdateRequest body, String idem) {
		Timer.Sample sample = Timer.start();
		return runUpdate(deviceId, body, idem)
				.doOnSuccess(r -> {
					sample.stop(metrics.getVersionUpdateTimer());
					metrics.recordVersionUpdateOutcome(
							r.compensated() ? OrchestratorMetrics.OUTCOME_COMPENSATED : OrchestratorMetrics.OUTCOME_SUCCESS);
				})
				.doOnError(e -> {
					sample.stop(metrics.getVersionUpdateTimer());
					metrics.recordVersionUpdateOutcome(OrchestratorMetrics.OUTCOME_FAILURE);
				});
	}

	private Mono<VersionUpdateResult> runUpdate(String deviceId, DeviceVersionUpdateRequest body, String idem) {
		log.info("Version update saga running deviceId={}, targetVersion={}, routerId={}",
				deviceId, body.targetVersion(), body.routerId());
		return devices.getDevice(deviceId)
				.switchIfEmpty(Mono.error(new DeviceNotFoundException(deviceId)))
				.flatMap(deviceBefore -> {
					String previousTarget = deviceBefore.targetSoftwareVersion();
					TargetVersionCommand forward = new TargetVersionCommand(body.targetVersion(), body.idempotencyKey());
					return devices.setDeviceTargetVersion(deviceId, forward)
							.flatMap(msg ->
									router.sendDeviceFirmwareUpdate(body.routerId(), deviceId, body.targetVersion())
											.doOnSuccess(commandIds -> log.info(
													"Router firmware update command accepted for deviceId={}, targetVersion={}, commandIds={}",
													deviceId, body.targetVersion(), commandIds))
											.map(commandIds -> new VersionUpdateResult(false, "Version update orchestrated",
													List.copyOf(commandIds)))
											.onErrorResume(err -> compensate(deviceId, previousTarget, body, err)));
				})
				.flatMap(result -> {
					if (idem == null || idem.isBlank()) {
						return Mono.just(result);
					}
					return idempotency.save(cacheKey(deviceId, idem), result)
							.doOnSuccess(v -> log.debug("Stored idempotent version-update result for deviceId={}", deviceId))
							.thenReturn(result);
				})
				.doOnSuccess(r -> log.info("Version update saga finished deviceId={}, compensated={}", deviceId, r.compensated()));
	}

	private Mono<VersionUpdateResult> compensate(String deviceId, String previousTarget,
			DeviceVersionUpdateRequest original, Throwable failure) {
		log.warn("Version update saga compensating deviceId={} after router error: {}", deviceId, failure.toString());
		if (previousTarget == null || previousTarget.isBlank()) {
			return Mono.error(failure);
		}
		TargetVersionCommand rollback = new TargetVersionCommand(previousTarget, rollbackKey(original.idempotencyKey()));
		return devices.setDeviceTargetVersion(deviceId, rollback)
				.doOnSuccess(m -> log.warn("Version update rollback applied deviceId={}, previousTargetRestored=true",
						deviceId))
				.map(r -> new VersionUpdateResult(true, "Compensated after router failure: " + failure.getMessage(),
						List.of()));
	}

	private static String rollbackKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return null;
		}
		return idempotencyKey + ":rollback";
	}

	private static String cacheKey(String deviceId, String idempotencyKey) {
		return deviceId + "::" + idempotencyKey;
	}
}
