package ru.dkalchenko.sender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SimpleEventSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleEventSenderApplication.class, args);
    }
}
