package ru.dkalchenko.collector.assistant;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.dkalchenko.collector.properties.ThroughputProperties;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Замеряет время обработки траншей сообщений из Kafka.
 * <p>
 * Транш — непрерывная серия сообщений. Завершается, когда в течение burst-idle-timeout-ms
 * не приходит ни одного сообщения. После завершения транша логирует: количество сообщений,
 * время обработки, throughput (msg/s).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ThroughputMeter {

    final ThroughputProperties properties;
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "throughput-meter-scheduler"));

    long burstStartTime;
    long burstCount;
    ScheduledFuture<?> flushTask;

    public ThroughputMeter(ThroughputProperties properties) {
        this.properties = properties;
        if (properties.enabled()) {
            log.info("ThroughputMeter enabled, burst-idle-timeout={} ms", properties.burstIdleTimeoutMs());
        }
    }

    /**
     * Вызвать после успешной обработки каждого сообщения.
     * При паузе между сообщениями >= burst-idle-timeout-ms логирует статистику по траншу.
     */
    public void record() {
        if (!properties.enabled()) {
            return;
        }

        synchronized (this) {
            burstCount++;
            if (burstCount == 1) {
                burstStartTime = System.nanoTime();
            }
            scheduleFlush();
        }
    }

    private void scheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        flushTask = scheduler.schedule(this::flushBurst, properties.burstIdleTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private void flushBurst() {
        long count;
        long elapsedNanos;
        synchronized (this) {
            count = burstCount;
            if (count == 0) {
                return;
            }
            elapsedNanos = System.nanoTime() - burstStartTime;
            burstCount = 0;
            flushTask = null;
        }

        long elapsedMs = elapsedNanos / 1_000_000;
        double msgPerSec = (count * 1_000_000_000.0) / elapsedNanos;

        log.info("Handled {} events, time = {} ms (~{} msg/s)",
                count, elapsedMs, String.format("%.0f", msgPerSec));
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
