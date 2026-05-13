package ru.dkalchenko.orchestrator.domain.port;

import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;

public interface VersionUpdateIdempotencyPort {

	Mono<VersionUpdateResult> find(String key);

	Mono<Void> save(String key, VersionUpdateResult result);
}
