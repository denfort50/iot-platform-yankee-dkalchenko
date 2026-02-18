package ru.dkalchenko.sender.config;

import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Загружает Avro-схему DeviceEvent для построения GenericRecord.
 * Схема берётся из classpath (JAR iot-avro-schemas из Nexus).
 */
@Configuration
public class AvroSchemaConfig {

    private static final Logger log = LoggerFactory.getLogger(AvroSchemaConfig.class);

    @Bean
    public Schema deviceEventSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("avro/DeviceEvent.avsc");
        log.info("Loading DeviceEvent schema from: {}", resource.getURL());
        try (InputStream in = resource.getInputStream()) {
            return new Schema.Parser().parse(in);
        }
    }
}
