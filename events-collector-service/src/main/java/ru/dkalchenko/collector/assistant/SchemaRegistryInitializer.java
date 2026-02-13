package ru.dkalchenko.collector.assistant;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.apache.avro.Schema;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * При старте приложения регистрирует Avro-схемы из classpath (resources/avro/*.avsc)
 * в Confluent Schema Registry. Имя subject для топика: {@code <topic>-value}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SchemaRegistryInitializer {

    static String AVRO_SCHEMAS_LOCATION = "classpath*:avro/*.avsc";

    static Map<String, String> SCHEMA_TO_SUBJECT = Map.of(
            "DeviceEvent", "events.v1-value",
            "DeviceDto", "devices.v1-value"
    );

    SchemaRegistryClient schemaRegistryClient;

    @PostConstruct
    void registerSchemas() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(AVRO_SCHEMAS_LOCATION);
            for (Resource resource : resources) {
                registerSchema(resource);
            }
        } catch (IOException e) {
            log.warn("Failed to load Avro schemas for registration: {}", e.getMessage());
        }
    }

    private void registerSchema(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".avsc")) {
            return;
        }
        String schemaName = filename.replace(".avsc", "");
        String subject = SCHEMA_TO_SUBJECT.get(schemaName);
        if (subject == null) {
            log.debug("Schema {} not mapped to any subject, skipping", schemaName);
            return;
        }

        String schemaJson = readResource(resource);
        Schema avroSchema = new Schema.Parser().parse(schemaJson);
        ParsedSchema parsedSchema = new AvroSchema(avroSchema);

        try {
            int id = schemaRegistryClient.register(subject, parsedSchema);
            log.info("Registered schema {} in Schema Registry, subject = {}, id = {}", schemaName, subject, id);
        } catch (RestClientException e) {
            if (e.getStatus() == 409) {
                // Схема уже зарегистрирована (или несовместимая версия)
                log.warn("Schema {} already exists for subject {}: {}", schemaName, subject, e.getMessage());
            } else {
                log.error("Failed to register schema {} for subject {}: {}", schemaName, subject, e.getMessage());
            }
        }
    }

    private static String readResource(Resource resource) throws IOException {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }
}
