package ru.dkalchenko.avro;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Утилита для регистрации Avro-схем из JAR/classpath в Confluent Schema Registry.
 * Вызывается из Gradle-задачи registerSchemas при сборке модуля.
 */
public final class SchemaRegistryBootstrap {

    private static final String SCHEMA_REGISTRY_URL_ENV = "SCHEMA_REGISTRY_URL";
    private static final String AVRO_RESOURCE_PREFIX = "avro/";
    private static final String AVRO_SUFFIX = ".avsc";
    private static final Map<String, String> SCHEMA_TO_SUBJECT = Map.of(
            "DeviceEvent", "events.v1-value",
            "DeviceInfo", "devices.v1-value",
            "DeadLetterMessage", "dlt.v1-value"
    );

    public static void main(String[] args) {
        String url = System.getenv(SCHEMA_REGISTRY_URL_ENV);
        if (url == null || url.isBlank()) {
            System.err.println("Set " + SCHEMA_REGISTRY_URL_ENV + " to Schema Registry URL (e.g. http://localhost:8081)");
            System.exit(1);
        }
        try {
            SchemaRegistryClient client = new CachedSchemaRegistryClient(url, 10);
            registerFromClasspath(client);
        } catch (Exception e) {
            System.err.println("Failed to register schemas: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void registerFromClasspath(SchemaRegistryClient client) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        // Ресурсы из JAR или из классов: avro/DeviceInfo.avsc и т.д.
        for (String schemaName : SCHEMA_TO_SUBJECT.keySet()) {
            String path = AVRO_RESOURCE_PREFIX + schemaName + AVRO_SUFFIX;
            try (var in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    System.err.println("Resource not found: " + path);
                    continue;
                }
                String json = readFully(new InputStreamReader(in, StandardCharsets.UTF_8));
                Schema avroSchema = new Schema.Parser().parse(json);
                ParsedSchema parsedSchema = new AvroSchema(avroSchema);
                String subject = SCHEMA_TO_SUBJECT.get(schemaName);
                try {
                    int id = client.register(subject, parsedSchema);
                    System.out.println("Registered schema " + schemaName + " -> " + subject + ", id=" + id);
                } catch (RestClientException e) {
                    if (e.getStatus() == 409) {
                        System.out.println("Schema " + schemaName + " already exists for " + subject + ", skipping");
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
