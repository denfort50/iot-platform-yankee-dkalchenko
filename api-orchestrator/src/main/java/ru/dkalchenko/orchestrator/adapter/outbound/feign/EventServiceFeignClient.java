package ru.dkalchenko.orchestrator.adapter.outbound.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
		name = "event-service",
		url = "${clients.event-service.url}",
		configuration = OrchestratorFeignConfiguration.class
)
public interface EventServiceFeignClient extends ru.dkalchenko.event.api.EventsApi {
}
