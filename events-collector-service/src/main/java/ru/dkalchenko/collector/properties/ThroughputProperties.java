package ru.dkalchenko.collector.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства для замера throughput при считывании сообщений из Kafka.
 * Транш — непрерывная серия сообщений; завершается, когда в течение burst-idle-timeout-ms
 * не приходит ни одного нового сообщения.
 */
@ConfigurationProperties(prefix = "collector.throughput")
public record ThroughputProperties(
        long burstIdleTimeoutMs,
        boolean enabled
) {
    private static final long DEFAULT_BURST_IDLE_TIMEOUT_MS = 1000;

    public ThroughputProperties {
        if (burstIdleTimeoutMs <= 0) {
            burstIdleTimeoutMs = DEFAULT_BURST_IDLE_TIMEOUT_MS;
        }
    }
}
