package ru.dkalchenko.orchestrator.adapter.outbound.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Contract;
import feign.Logger;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class OrchestratorFeignConfiguration {

	@Bean
	Logger.Level feignLoggerLevel() {
		return Logger.Level.BASIC;
	}

	@Bean
	Contract feignContract() {
		return new Contract.Default();
	}

	/** Для SpringEncoder в OpenFeign при WebFlux без MVC. */
	@Bean
	HttpMessageConverters feignHttpMessageConverters(ObjectMapper objectMapper) {
		return new HttpMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper));
	}
}
