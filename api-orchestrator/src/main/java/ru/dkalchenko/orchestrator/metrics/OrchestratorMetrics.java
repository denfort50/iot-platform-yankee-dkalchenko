package ru.dkalchenko.orchestrator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Метрики саги обновления версии и идемпотентных повторов.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrchestratorMetrics {

	public static final String TAG_OUTCOME = "outcome";
	public static final String OUTCOME_SUCCESS = "success";
	public static final String OUTCOME_COMPENSATED = "compensated";
	public static final String OUTCOME_FAILURE = "failure";

	MeterRegistry meterRegistry;
	Map<String, Counter> versionUpdateOutcomeCounters = new ConcurrentHashMap<>();
	Counter idempotentReplayCounter;
	@Getter
    Timer versionUpdateTimer;

	public OrchestratorMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		this.idempotentReplayCounter = Counter.builder("orchestrator.version.update.idempotent.replay.total")
				.description("Idempotent cache hits for version update")
				.register(meterRegistry);
		this.versionUpdateTimer = Timer.builder("orchestrator.version.update.duration")
				.description("Duration of version update saga execution")
				.register(meterRegistry);
	}

	public void recordVersionUpdateOutcome(String outcome) {
		versionUpdateOutcomeCounters.computeIfAbsent(outcome,
				o -> Counter.builder("orchestrator.version.update.total")
						.description("Version update saga outcomes")
						.tag(TAG_OUTCOME, o)
						.register(meterRegistry)
		).increment();
	}

	public void recordIdempotentReplay() {
		idempotentReplayCounter.increment();
	}

}
