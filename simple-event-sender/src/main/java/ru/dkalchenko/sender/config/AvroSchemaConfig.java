package ru.dkalchenko.sender.config;

import org.apache.avro.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Загружает Avro-схему DeviceEvent для построения GenericRecord.
 */
@Configuration
public class AvroSchemaConfig {

    @Bean
    public Schema deviceEventSchema() throws IOException {
        try (InputStream in = new ClassPathResource("avro/DeviceEvent.avsc").getInputStream()) {
            return new Schema.Parser().parse(in);
        }
    }
}
