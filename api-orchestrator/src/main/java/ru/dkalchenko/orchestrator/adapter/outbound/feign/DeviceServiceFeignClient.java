package ru.dkalchenko.orchestrator.adapter.outbound.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
		name = "device-service",
		url = "${clients.device-service.url}",
		configuration = OrchestratorFeignConfiguration.class
)
public interface DeviceServiceFeignClient extends ru.dkalchenko.device.api.DefaultApi {
}
