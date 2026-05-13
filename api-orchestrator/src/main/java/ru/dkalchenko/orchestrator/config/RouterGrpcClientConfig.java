package ru.dkalchenko.orchestrator.config;

import api.v1.RouterManagementServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouterGrpcClientConfig {

	@Bean(destroyMethod = "shutdown")
	public ManagedChannel routerManagementChannel(
			@Value("${clients.router-management.host}") String host,
			@Value("${clients.router-management.port}") int port,
			OpenTelemetry openTelemetry) {
		GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);
		return ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext()
				.intercept(grpcTelemetry.newClientInterceptor())
				.build();
	}

	@Bean
	public RouterManagementServiceGrpc.RouterManagementServiceBlockingStub routerManagementStub(
			ManagedChannel routerManagementChannel) {
		return RouterManagementServiceGrpc.newBlockingStub(routerManagementChannel);
	}
}
