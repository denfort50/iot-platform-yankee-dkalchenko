package ru.dkalchenko.orchestrator.adapter.outbound.router;

import api.v1.RouterManagement;
import api.v1.RouterManagementServiceGrpc;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import ru.dkalchenko.orchestrator.application.exception.RouterNotFoundException;
import ru.dkalchenko.orchestrator.config.ReactiveBearerContext;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientRouterCommandAdapterTest {

	static final AtomicReference<String> sendCommandError = new AtomicReference<>();

	static Server grpcServer;
	static io.grpc.ManagedChannel grpcChannel;
	static ResilientRouterCommandAdapter adapter;

	@BeforeAll
	static void startGrpc() throws IOException {
		sendCommandError.set(null);
		grpcServer = ServerBuilder.forPort(0)
				.addService(new RouterManagementServiceGrpc.RouterManagementServiceImplBase() {
					@Override
					public void sendCommand(RouterManagement.SendCommandRequest request,
							StreamObserver<RouterManagement.SendCommandResponse> responseObserver) {
						String err = sendCommandError.get();
						if (err != null) {
							responseObserver.onNext(RouterManagement.SendCommandResponse.newBuilder()
									.setError(err)
									.build());
						} else {
							responseObserver.onNext(RouterManagement.SendCommandResponse.newBuilder()
									.addCommandIds("cmd-1")
									.build());
						}
						responseObserver.onCompleted();
					}
				})
				.build()
				.start();
		int chosenPort = grpcServer.getPort();
		grpcChannel = io.grpc.ManagedChannelBuilder.forAddress("localhost", chosenPort).usePlaintext().build();
		RouterManagementServiceGrpc.RouterManagementServiceBlockingStub stub =
				RouterManagementServiceGrpc.newBlockingStub(grpcChannel);
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
		TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(
				TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build());
		adapter = new ResilientRouterCommandAdapter(stub, cbRegistry, tlRegistry);
	}

	@AfterAll
	static void stopGrpc() throws InterruptedException {
		if (grpcChannel != null) {
			grpcChannel.shutdown();
			try {
				grpcChannel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (grpcServer != null) {
			grpcServer.shutdownNow().awaitTermination();
		}
	}

	@Test
	void whenSendCommandAndWhenGrpcReturnsRouterNotFoundThenMapsException() {
		sendCommandError.set("Router not found");
		try {
			StepVerifier.create(adapter.sendCommand("rid", "PING", "{}").contextWrite(ctx()))
					.expectError(RouterNotFoundException.class)
					.verify();
		} finally {
			sendCommandError.set(null);
		}
	}

	@Test
	void whenSendCommandAndWhenGrpcReturnsInvalidRouterIdThenMapsIllegalArgument() {
		sendCommandError.set("invalid router_id");
		try {
			StepVerifier.create(adapter.sendCommand("rid", "PING", "{}").contextWrite(ctx()))
					.expectError(IllegalArgumentException.class)
					.verify();
		} finally {
			sendCommandError.set(null);
		}
	}

	@Test
	void whenSendCommandAndWhenOkThenReturnsCommandIds() {
		sendCommandError.set(null);
		StepVerifier.create(adapter.sendCommand("router-1", "PING", "{}").contextWrite(ctx()))
				.assertNext(ids -> assertThat(ids).containsExactly("cmd-1"))
				.verifyComplete();
	}

	private static Context ctx() {
		return Context.of(ReactiveBearerContext.KEY, "token");
	}
}
