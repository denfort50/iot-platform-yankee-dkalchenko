package ru.dkalchenko.orchestrator.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.RouterCommandMessage;
import ru.dkalchenko.orchestrator.application.port.in.CommandUseCase;
import ru.dkalchenko.orchestrator.domain.port.RouterCommandPort;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommandApplicationService implements CommandUseCase {

	RouterCommandPort router;

	@Override
	public Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson) {
		return router.sendCommand(routerId, commandType, payloadJson)
				.doOnSubscribe(s -> log.debug("sendCommand service routerId={}, commandType={}", routerId, commandType));
	}

	@Override
	public Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson, String bearerToken) {
		return router.sendCommand(routerId, commandType, payloadJson, bearerToken)
				.doOnSubscribe(s -> log.debug("sendCommand service routerId={}, commandType={}, bearerPresent={}",
						routerId, commandType, bearerToken != null && !bearerToken.isBlank()));
	}

	@Override
	public Mono<String> registerRouter(String inventoryNumber, String name, String model, String location, String bearerToken) {
		return router.registerRouter(inventoryNumber, name, model, location, bearerToken)
				.doOnSubscribe(s -> log.debug("registerRouter service inventoryNumber={}, name={}, bearerPresent={}",
						inventoryNumber, name, bearerToken != null && !bearerToken.isBlank()));
	}

	@Override
	public Mono<List<RouterCommandMessage>> pollCommands(String routerId, String bearerToken) {
		return router.pollCommands(routerId, bearerToken)
				.doOnSubscribe(s -> log.debug("pollCommands service routerId={}, bearerPresent={}",
						routerId, bearerToken != null && !bearerToken.isBlank()));
	}

	@Override
	public Mono<Boolean> ackCommand(String routerId, String commandId, String bearerToken) {
		return router.ackCommand(routerId, commandId, bearerToken)
				.doOnSubscribe(s -> log.debug("ackCommand service routerId={}, commandId={}, bearerPresent={}",
						routerId, commandId, bearerToken != null && !bearerToken.isBlank()));
	}
}
