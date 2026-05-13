package ru.dkalchenko.orchestrator.config;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(0)
public class BearerReactiveContextWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return resolveBearer(exchange).flatMap(
				token -> chain.filter(exchange)
						.contextWrite(ctx -> token.isBlank() ? ctx : ctx.put(ReactiveBearerContext.KEY, token)));
	}

	private static Mono<String> resolveBearer(ServerWebExchange exchange) {
		return exchange.getPrincipal()
				.filter(JwtAuthenticationToken.class::isInstance)
				.cast(JwtAuthenticationToken.class)
				.map(auth -> auth.getToken().getTokenValue())
				.switchIfEmpty(Mono.fromCallable(() -> headerBearer(exchange))
						.filter(t -> !t.isBlank()))
				.defaultIfEmpty("");
	}

	private static String headerBearer(ServerWebExchange exchange) {
		String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
			return "";
		}
		return auth.substring("Bearer ".length()).trim();
	}
}
