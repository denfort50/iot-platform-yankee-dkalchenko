package ru.dkalchenko.failedevents.unit.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.dkalchenko.failedevents.metrics.FailedEventsMetrics;

import static org.assertj.core.api.Assertions.assertThat;

class FailedEventsMetricsTest {

	@Test
	void countersAreIncremented() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		FailedEventsMetrics metrics = new FailedEventsMetrics(registry);

		metrics.incrementFailedEventSaved();
		metrics.incrementFailedDeviceSaved();
		metrics.incrementDltReceivedInvalid();
		metrics.incrementDltReceivedUnknown();
		metrics.incrementProcessingError();

		assertThat(registry.get("failed.events.processor.saved.events.total").counter().count())
				.isEqualTo(1.0);
		assertThat(registry.get("failed.events.processor.saved.devices.total").counter().count())
				.isEqualTo(1.0);
		assertThat(registry.get("failed.events.processor.dlt.invalid.total").counter().count())
				.isEqualTo(1.0);
		assertThat(registry.get("failed.events.processor.dlt.unknown.total").counter().count())
				.isEqualTo(1.0);
		assertThat(registry.get("failed.events.processor.errors.total").counter().count())
				.isEqualTo(1.0);
	}
}

