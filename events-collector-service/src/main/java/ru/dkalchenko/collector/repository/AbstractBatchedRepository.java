package ru.dkalchenko.collector.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.dkalchenko.collector.properties.BatchProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Базовая логика батчевой записи в ClickHouse: буфер, планировщик, сброс по размеру или таймауту.
 * Подкласс реализует {@link #doFlush(List)} — одну батчевую вставку в БД.
 */
@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class AbstractBatchedRepository<T> {

    BatchProperties batchProperties;
    ConcurrentLinkedQueue<T> buffer = new ConcurrentLinkedQueue<>();
    ScheduledExecutorService scheduler;

    protected AbstractBatchedRepository(BatchProperties batchProperties, String flushThreadName) {
        this.batchProperties = batchProperties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, flushThreadName);
            t.setDaemon(false);
            return t;
        });
    }

    @PostConstruct
    void startPeriodicFlush() {
        long intervalMs = batchProperties.flushIntervalMs();
        scheduler.scheduleWithFixedDelay(
                this::flushIfNotEmpty,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("{} batch flush: interval = {} ms, batchSize = {}", getFlushLogName(), intervalMs, batchProperties.batchSize());
    }

    protected final void addAndFlushIfFull(T item) {
        buffer.add(item);
        if (buffer.size() >= batchProperties.batchSize()) {
            flushIfNotEmpty();
        }
    }

    protected final void flushIfNotEmpty() {
        List<T> batch = drainUpTo(batchProperties.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        try {
            doFlush(batch);
            log.trace("Flushed {} {} to ClickHouse", batch.size(), getFlushLogName());
        } catch (Exception e) {
            log.error("Failed to flush {} {} to ClickHouse", batch.size(), getFlushLogName(), e);
            buffer.addAll(batch);
        }
    }

    protected final List<T> drainUpTo(int max) {
        List<T> result = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            T item = buffer.poll();
            if (item == null) break;
            result.add(item);
        }
        return result;
    }

    @PreDestroy
    public final void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        while (!buffer.isEmpty()) {
            flushIfNotEmpty();
        }
    }

    /** Имя сущности для логов (например, "events", "outbox records"). */
    protected abstract String getFlushLogName();

    /** Выполняет батчевую вставку в ClickHouse. */
    protected abstract void doFlush(List<T> batch);
}
