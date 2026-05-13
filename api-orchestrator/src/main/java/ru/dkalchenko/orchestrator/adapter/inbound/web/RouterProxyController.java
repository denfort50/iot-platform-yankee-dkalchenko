package ru.dkalchenko.orchestrator.adapter.inbound.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.adapter.inbound.api.generated.RoutersApiDelegate;
import ru.dkalchenko.orchestrator.api.model.RegisterRouterRequestBody;
import ru.dkalchenko.orchestrator.api.model.RegisterRouterResponseBody;
import ru.dkalchenko.orchestrator.application.port.in.CommandUseCase;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouterProxyController implements RoutersApiDelegate {

	CommandUseCase commands;

	@Override
	public Mono<ResponseEntity<RegisterRouterResponseBody>> registerRouter(
			Mono<RegisterRouterRequestBody> registerRouterRequestBody,
			ServerWebExchange exchange) {
		return resolveBearer(exchange)
				.defaultIfEmpty("")
				.flatMap(token -> registerRouterRequestBody
						.doOnNext(body -> log.debug("registerRouter request inventoryNumber={}, name={}",
								body.getInventoryNumber(), body.getName()))
						.flatMap(body -> commands.registerRouter(
								body.getInventoryNumber(),
								body.getName(),
								body.getModel(),
								body.getLocation(),
								token))
						.doOnNext(routerId -> log.debug("registerRouter completed, routerId={}", routerId))
						.map(routerId -> ResponseEntity.ok(new RegisterRouterResponseBody().routerId(routerId))));
	}

	private static Mono<String> resolveBearer(ServerWebExchange exchange) {
		return exchange.getPrincipal()
				.filter(Authentication.class::isInstance)
				.map(Authentication.class::cast)
				.map(RouterProxyController::extractBearerFromAuthentication)
				.filter(token -> token != null && !token.isBlank())
				.switchIfEmpty(Mono.justOrEmpty(extractBearerFromHeader(exchange)));
	}

	private static String extractBearerFromAuthentication(Authentication authentication) {
		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			return jwtAuthenticationToken.getToken().getTokenValue();
		}
		return null;
	}

	private static String extractBearerFromHeader(ServerWebExchange exchange) {
		String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
		if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return auth.substring(7).trim();
		}
		return null;
	}
}
