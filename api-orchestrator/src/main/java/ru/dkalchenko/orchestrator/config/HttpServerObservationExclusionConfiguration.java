package ru.dkalchenko.orchestrator.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Отключает HTTP server observations (метрики + трейсы) для шумных actuator-эндпоинтов.
 */
@Configuration
public class HttpServerObservationExclusionConfiguration {

	private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

	/**
	 * Общая фабрика: используется в основном контексте и в дочернем management-контексте
	 * (см. {@link ActuatorManagementObservationExclusionConfiguration}).
	 */
	static ObservationPredicate actuatorHealthAndPrometheusExclusionPredicate(String actuatorBasePath) {
		String base = normalizeBasePath(actuatorBasePath);
		String healthPrefix = base + "/health";
		String prometheusPrefix = base + "/prometheus";
		return (name, context) -> {
			if (!HTTP_SERVER_REQUESTS.equals(name)) {
				return true;
			}
			if (!(context instanceof ServerRequestObservationContext observationContext)) {
				return true;
			}
			var carrier = observationContext.getCarrier();
			if (carrier == null) {
				return true;
			}
			var path = carrier.getURI().getPath();
			if (path == null || path.isEmpty()) {
				return true;
			}
			if (path.equals(healthPrefix) || path.startsWith(healthPrefix + "/")) {
				return false;
			}
			if (path.equals(prometheusPrefix) || path.startsWith(prometheusPrefix + "/")) {
				return false;
			}
			return true;
		};
	}

	@Bean
	ObservationPredicate excludeActuatorHealthAndPrometheusFromObservations(
			@Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {
		return actuatorHealthAndPrometheusExclusionPredicate(actuatorBasePath);
	}

	static String normalizeBasePath(String actuatorBasePath) {
		if (actuatorBasePath == null || actuatorBasePath.isBlank()) {
			return "/actuator";
		}
		String trimmed = actuatorBasePath.trim();
		if (!trimmed.startsWith("/")) {
			trimmed = "/" + trimmed;
		}
		if (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
