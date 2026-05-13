package ru.dkalchenko.orchestrator.adapter.outbound.idempotency;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;
import ru.dkalchenko.orchestrator.domain.port.VersionUpdateIdempotencyPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InMemoryVersionUpdateIdempotencyAdapter implements VersionUpdateIdempotencyPort {

	Map<String, VersionUpdateResult> results = new ConcurrentHashMap<>();

	@Override
	public Mono<VersionUpdateResult> find(String key) {
		VersionUpdateResult result = results.get(key);
		if (result != null) {
			log.debug("Version-update idempotency cache hit");
		}
		return result == null ? Mono.empty() : Mono.just(result);
	}

	@Override
	public Mono<Void> save(String key, VersionUpdateResult result) {
		results.put(key, result);
		log.debug("Version-update idempotency cache store");
		return Mono.empty();
	}
}
