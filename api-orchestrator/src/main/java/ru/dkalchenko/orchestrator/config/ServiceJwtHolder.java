package ru.dkalchenko.orchestrator.config;

import lombok.experimental.UtilityClass;

/**
 * Кратковременно выставляется на потоке blocking-вызова (Feign); значение берёт из {@link ReactiveBearerContext}.
 */
@UtilityClass
public class ServiceJwtHolder {

	private final ThreadLocal<String> TOKEN = new ThreadLocal<>();

	public static void setBearerToken(String tokenValue) {
		TOKEN.set(tokenValue);
	}

	public static String getBearerToken() {
		return TOKEN.get();
	}

	public static void clear() {
		TOKEN.remove();
	}
}
