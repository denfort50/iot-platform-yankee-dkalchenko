package ru.dkalchenko.orchestrator.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;

/**
 * Дочерний management-контекст (отдельный {@code management.server.port}): тот же предикат,
 * чтобы не создавать трейсы/observations на health и prometheus.
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class ActuatorManagementObservationExclusionConfiguration {

	@Bean
	ObservationPredicate excludeActuatorHealthAndPrometheusFromObservationsInChildManagementContext(
			@Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {
		return HttpServerObservationExclusionConfiguration.actuatorHealthAndPrometheusExclusionPredicate(
				actuatorBasePath);
	}
}
