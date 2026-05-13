package ru.dkalchenko.orchestrator.application.port.in;

import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.RouterCommandMessage;

import java.util.List;

public interface CommandUseCase {

	Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson);

	Mono<List<String>> sendCommand(String routerId, String commandType, String payloadJson, String bearerToken);

	Mono<String> registerRouter(String inventoryNumber, String name, String model, String location, String bearerToken);

	Mono<List<RouterCommandMessage>> pollCommands(String routerId, String bearerToken);

	Mono<Boolean> ackCommand(String routerId, String commandId, String bearerToken);
}
