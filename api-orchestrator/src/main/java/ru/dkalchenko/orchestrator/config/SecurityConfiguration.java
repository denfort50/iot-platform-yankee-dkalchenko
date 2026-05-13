package ru.dkalchenko.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfiguration {

	/** Micrometer: без спанов filter chain / authorize / secured request и т.п. */
	@Bean
	SecurityObservationSettings securityObservationSettings() {
		return SecurityObservationSettings.noObservations();
	}

	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchange -> exchange
						.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.pathMatchers("/actuator/prometheus", "/actuator/metrics/**").permitAll()
						.anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
		return http.build();
	}
}
