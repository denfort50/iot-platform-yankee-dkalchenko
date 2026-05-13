package ru.dkalchenko.orchestrator.adapter.outbound.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import ru.dkalchenko.orchestrator.config.ServiceJwtHolder;

@Component
public class BearerForwardingInterceptor implements RequestInterceptor {

	@Override
	public void apply(RequestTemplate template) {
		String token = ServiceJwtHolder.getBearerToken();
		if (token != null && !token.isBlank()) {
			template.header("Authorization", "Bearer " + token);
		}
	}
}
