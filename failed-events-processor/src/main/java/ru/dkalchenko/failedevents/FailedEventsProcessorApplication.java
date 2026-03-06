package ru.dkalchenko.failedevents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FailedEventsProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(FailedEventsProcessorApplication.class, args);
	}
}
