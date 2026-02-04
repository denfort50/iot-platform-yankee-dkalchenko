package ru.dkalchenko.collector.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.dkalchenko.collector.repository.OutboxRepository;

/**
 * Набор кастомных метрик для events-collector-service.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CollectorMetrics {

    Counter eventsProcessedTotal;
    Counter eventsDuplicatesTotal;
    Counter outboxPublishSuccessTotal;
    Counter outboxPublishFailTotal;

    public CollectorMetrics(MeterRegistry meterRegistry, OutboxRepository outboxRepository) {
        this.eventsProcessedTotal = Counter.builder("events.processed.total")
                .description("Total number of successfully processed raw events")
                .register(meterRegistry);

        this.eventsDuplicatesTotal = Counter.builder("events.duplicates.total")
                .description("Total number of events for already seen devices (duplicates for outbox)")
                .register(meterRegistry);

        this.outboxPublishSuccessTotal = Counter.builder("outbox.publish.success.total")
                .description("Total number of successfully published outbox records to Kafka")
                .register(meterRegistry);

        this.outboxPublishFailTotal = Counter.builder("outbox.publish.fail.total")
                .description("Total number of failed publish attempts from outbox to Kafka")
                .register(meterRegistry);

        // Gauge, который отражает текущее количество необработанных записей в outbox
        try {
            Gauge.builder("outbox.pending.count", outboxRepository, repo -> {
                try {
                    long count = repo.countPending();
                    log.trace("Gauge outbox.pending.count called, returning: {}", count);
                    return count;
                } catch (Exception e) {
                    log.warn("Failed to get outbox pending count: {}", e.getMessage());
                    return 0L;
                }
            })
                    .description("Current number of pending outbox records")
                    .register(meterRegistry);
            log.info("Successfully registered gauge: outbox.pending.count");
        } catch (Exception e) {
            log.error("Failed to register gauge outbox.pending.count", e);
        }
    }

    /**
     * Увеличивает счётчик успешно обработанных событий.
     */
    public void incrementEventsProcessed() {
        eventsProcessedTotal.increment();
    }

    /**
     * Увеличивает счётчик событий-дубликатов (по deviceId) для outbox.
     */
    public void incrementEventsDuplicate() {
        eventsDuplicatesTotal.increment();
    }

    /**
     * Увеличивает счётчик успешных публикаций записей из outbox.
     */
    public void incrementOutboxPublishSuccess() {
        outboxPublishSuccessTotal.increment();
    }

    /**
     * Увеличивает счётчик неуспешных публикаций записей из outbox.
     */
    public void incrementOutboxPublishFail() {
        outboxPublishFailTotal.increment();
    }
}

