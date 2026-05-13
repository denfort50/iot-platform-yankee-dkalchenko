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
import ru.dkalchenko.orchestrator.adapter.inbound.api.generated.CommandsApiDelegate;
import ru.dkalchenko.orchestrator.api.model.CommandMessage;
import ru.dkalchenko.orchestrator.api.model.CommandsAckRequestBody;
import ru.dkalchenko.orchestrator.api.model.CommandsAckResponseBody;
import ru.dkalchenko.orchestrator.api.model.CommandsPollRequestBody;
import ru.dkalchenko.orchestrator.api.model.CommandsPollResponseBody;
import ru.dkalchenko.orchestrator.application.port.in.CommandUseCase;
import ru.dkalchenko.orchestrator.api.model.SendCommandRequestBody;
import ru.dkalchenko.orchestrator.api.model.SendCommandResponseBody;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommandProxyController implements CommandsApiDelegate {

	CommandUseCase commands;

	@Override
	public Mono<ResponseEntity<SendCommandResponseBody>> sendCommand(Mono<SendCommandRequestBody> sendCommandRequestBody,
			ServerWebExchange exchange) {
		return resolveBearer(exchange)
				.defaultIfEmpty("")
				.flatMap(token -> sendCommandRequestBody
						.doOnNext(body -> log.debug("sendCommand request routerId={}, commandType={}", body.getRouterId(),
								body.getCommandType()))
						.flatMap(body -> commands.sendCommand(body.getRouterId(), body.getCommandType(), body.getPayload(), token))
						.doOnNext(ids -> log.debug("sendCommand completed, commandIdsCount={}", ids.size()))
						.map(ids -> ResponseEntity.ok(new SendCommandResponseBody().commandIds(ids))));
	}

	@Override
	public Mono<ResponseEntity<CommandsPollResponseBody>> pollCommands(
			Mono<CommandsPollRequestBody> commandsPollRequestBody,
			ServerWebExchange exchange) {
		return resolveBearer(exchange)
				.defaultIfEmpty("")
				.flatMap(token -> commandsPollRequestBody
						.doOnNext(body -> log.debug("pollCommands request routerId={}", body.getRouterId()))
						.flatMap(body -> commands.pollCommands(body.getRouterId(), token))
						.doOnNext(items -> log.debug("pollCommands completed, commandsCount={}", items.size()))
						.map(items -> ResponseEntity.ok(new CommandsPollResponseBody()
								.commands(items.stream()
										.map(item -> new CommandMessage()
												.commandId(item.commandId())
												.commandType(item.commandType())
												.payload(item.payload())
												.status(item.status()))
										.toList()))));
	}

	@Override
	public Mono<ResponseEntity<CommandsAckResponseBody>> ackCommand(
			Mono<CommandsAckRequestBody> commandsAckRequestBody,
			ServerWebExchange exchange) {
		return resolveBearer(exchange)
				.defaultIfEmpty("")
				.flatMap(token -> commandsAckRequestBody
						.doOnNext(body -> log.debug("ackCommand request routerId={}, commandId={}",
								body.getRouterId(), body.getCommandId()))
						.flatMap(body -> commands.ackCommand(body.getRouterId(), body.getCommandId(), token))
						.doOnNext(success -> log.debug("ackCommand completed, success={}", success))
						.map(success -> ResponseEntity.ok(new CommandsAckResponseBody().success(success))));
	}

	private static Mono<String> resolveBearer(ServerWebExchange exchange) {
		return exchange.getPrincipal()
				.filter(Authentication.class::isInstance)
				.map(Authentication.class::cast)
				.map(CommandProxyController::extractBearerFromAuthentication)
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
