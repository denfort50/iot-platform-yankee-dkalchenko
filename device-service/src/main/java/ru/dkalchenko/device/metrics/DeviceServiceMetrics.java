package ru.dkalchenko.device.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Метрики CRUD операций: успех/неуспех, время выполнения, ошибки БД.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceServiceMetrics {

	static final String TAG_OPERATION = "operation";
	static final String TAG_STATUS = "status";
	static final String STATUS_SUCCESS = "success";
	static final String STATUS_FAILURE = "failure";

	MeterRegistry meterRegistry;
	Map<String, Counter> successCounters = new ConcurrentHashMap<>();
	Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
	Map<String, Timer> timers = new ConcurrentHashMap<>();

	public DeviceServiceMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void recordSuccess(String operation) {
		successCounters.computeIfAbsent(operation,
				op -> Counter.builder("device.management.operations.total")
						.description("Total CRUD operations")
						.tag(TAG_OPERATION, op)
						.tag(TAG_STATUS, STATUS_SUCCESS)
						.register(meterRegistry)
		).increment();
	}

	public void recordFailure(String operation) {
		failureCounters.computeIfAbsent(operation,
				op -> Counter.builder("device.management.operations.total")
						.description("Total CRUD operations")
						.tag(TAG_OPERATION, op)
						.tag(TAG_STATUS, STATUS_FAILURE)
						.register(meterRegistry)
		).increment();
	}

	public void recordDbError(String operation) {
		Counter.builder("device.management.db.errors.total")
				.description("Database errors by operation")
				.tag(TAG_OPERATION, operation)
				.register(meterRegistry)
				.increment();
	}

	public Timer getTimer(String operation) {
		return timers.computeIfAbsent(operation,
				op -> Timer.builder("device.management.operations.duration")
						.description("CRUD operation duration")
						.tag(TAG_OPERATION, op)
						.register(meterRegistry)
		);
	}
}
