package ru.dkalchenko.collector.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Конфигурация трейсинга: отключает автоматический трейсинг для scheduled задач,
 * чтобы в Grafana/Tempo не появлялись трейсы вида "task instance-registry.heartbeat"
 * и "task outbox-processor.process-outbox".
 */
@Slf4j
@Order
@Configuration
public class TracingConfig implements SchedulingConfigurer {

    /**
     * Настройка ObservationRegistry: исключает scheduled задачи из трейсинга.
     */
    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> ignoreScheduledTasks() {
        return registry -> {
            registry.observationConfig()
                    .observationPredicate((name, _) -> {
                        if (name != null && (name.equals("tasks.scheduled.execution")
                                || name.startsWith("tasks.scheduled."))) {
                            log.trace("Filtering out scheduled task observation: {}", name);
                            return false;
                        }
                        return true;
                    });
        };
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // Устанавливаем NOOP ObservationRegistry для scheduled задач, чтобы они не создавали трейсы
        taskRegistrar.setObservationRegistry(ObservationRegistry.NOOP);
        // INFO, чтобы точно увидеть в логах при старте (по умолчанию уровень INFO)
        log.info("Disabled observations for scheduled tasks (@Scheduled)");
    }
}
