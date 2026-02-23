package ru.dkalchenko.device.audit;

import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Предоставляет контекст запроса для аудита: субъект (actor) и trace ID
 * для корреляции с логами и трейсами.
 */
@Component
public class AuditContextProvider {

	private static final String MDC_TRACE_ID = "traceId";
	private static final String UNKNOWN_ACTOR = "anonymous";

	/**
	 * Имя текущего субъекта (пользователь/сервис). Из JWT или "anonymous".
	 */
	public String getCurrentActor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank()) {
			return auth.getName();
		}
		return UNKNOWN_ACTOR;
	}

	/**
	 * Trace ID текущего запроса из MDC (Micrometer Tracing).
	 */
	@Nullable
	public String getTraceId() {
		return MDC.get(MDC_TRACE_ID);
	}
}
