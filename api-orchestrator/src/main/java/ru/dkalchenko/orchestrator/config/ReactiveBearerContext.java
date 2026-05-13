package ru.dkalchenko.orchestrator.config;

/**
 * JWT для проксирования на синхронные вызовы (Feign, gRPC) с {@code subscribeOn}, где ThreadLocal недоступен.
 */
public final class ReactiveBearerContext {

	public static final String KEY = ReactiveBearerContext.class.getName() + ".bearerJwt";

	private ReactiveBearerContext() {
	}
}
