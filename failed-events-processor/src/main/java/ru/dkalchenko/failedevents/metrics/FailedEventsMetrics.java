package ru.dkalchenko.failedevents.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * Метрики failed-events-processor для мониторинга.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FailedEventsMetrics {

	Counter failedEventSaved;
	Counter failedDeviceSaved;
	Counter dltReceivedInvalid;
	Counter dltReceivedUnknown;
	Counter processingError;

	public FailedEventsMetrics(MeterRegistry meterRegistry) {
		this.failedEventSaved = Counter.builder("failed.events.processor.saved.events.total")
				.description("Total number of failed events saved to MinIO (failed-events bucket)")
				.register(meterRegistry);
		this.failedDeviceSaved = Counter.builder("failed.events.processor.saved.devices.total")
				.description("Total number of failed devices saved to MinIO (failed-devices bucket)")
				.register(meterRegistry);
		this.dltReceivedInvalid = Counter.builder("failed.events.processor.dlt.invalid.total")
				.description("Total number of DLT messages with null or invalid value")
				.register(meterRegistry);
		this.dltReceivedUnknown = Counter.builder("failed.events.processor.dlt.unknown.total")
				.description("Total number of DLT messages with unknown payload schema (not DeviceEvent nor DeviceInfo)")
				.register(meterRegistry);
		this.processingError = Counter.builder("failed.events.processor.errors.total")
				.description("Total number of processing errors (e.g. MinIO upload failure)")
				.register(meterRegistry);
	}

	public void incrementFailedEventSaved() {
		failedEventSaved.increment();
	}

	public void incrementFailedDeviceSaved() {
		failedDeviceSaved.increment();
	}

	public void incrementDltReceivedInvalid() {
		dltReceivedInvalid.increment();
	}

	public void incrementDltReceivedUnknown() {
		dltReceivedUnknown.increment();
	}

	public void incrementProcessingError() {
		processingError.increment();
	}
}
