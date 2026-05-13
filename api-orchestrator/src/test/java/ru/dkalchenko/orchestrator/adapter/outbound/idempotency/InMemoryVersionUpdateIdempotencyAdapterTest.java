package ru.dkalchenko.orchestrator.adapter.outbound.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import ru.dkalchenko.orchestrator.application.model.VersionUpdateResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVersionUpdateIdempotencyAdapterTest {

	InMemoryVersionUpdateIdempotencyAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new InMemoryVersionUpdateIdempotencyAdapter();
	}

	@Test
	void whenSaveThenFindAndReturnsStoredResult() {
		VersionUpdateResult stored = new VersionUpdateResult(false, "ok", List.of("c1"));

		StepVerifier.create(adapter.save("k1", stored).then(adapter.find("k1")))
				.expectNext(stored)
				.verifyComplete();
	}

	@Test
	void whenFindAndWhenMissingThenReturnsEmpty() {
		StepVerifier.create(adapter.find("missing"))
				.verifyComplete();
	}

	@Test
	void whenDifferentKeysThenDoNotOverlap() {
		VersionUpdateResult a = new VersionUpdateResult(false, "a", List.of());
		VersionUpdateResult b = new VersionUpdateResult(true, "b", List.of());

		StepVerifier.create(adapter.save("x::1", a).then(adapter.save("x::2", b)).then(adapter.find("x::1")))
				.expectNext(a)
				.verifyComplete();
		StepVerifier.create(adapter.find("x::2"))
				.expectNext(b)
				.verifyComplete();
	}

	@Test
	void whenOverwriteThenUpdatesValue() {
		VersionUpdateResult first = new VersionUpdateResult(false, "v1", List.of());
		VersionUpdateResult second = new VersionUpdateResult(false, "v2", List.of("z"));

		StepVerifier.create(adapter.save("key", first).then(adapter.save("key", second)).then(adapter.find("key")))
				.assertNext(r -> assertThat(r.message()).isEqualTo("v2"))
				.verifyComplete();
	}
}
