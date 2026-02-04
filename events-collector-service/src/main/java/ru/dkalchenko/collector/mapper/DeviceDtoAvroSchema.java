package ru.dkalchenko.collector.mapper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Загружает Avro-схему DeviceDto из classpath (avro/DeviceDto.avsc) и строит GenericRecord по deviceId.
 */
@Getter
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceDtoAvroSchema {

    static String DEVICE_DTO_SCHEMA_PATH = "avro/DeviceDto.avsc";

    Schema schema;

    public DeviceDtoAvroSchema() throws IOException {
        try (InputStream in = new ClassPathResource(DEVICE_DTO_SCHEMA_PATH).getInputStream()) {
            String schemaJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            this.schema = new Schema.Parser().parse(schemaJson);
        }
    }

    /**
     * Строит GenericRecord по схеме DeviceDto для отправки в Kafka.
     */
    public GenericRecord createRecord(String deviceId) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("deviceId", deviceId != null ? deviceId : "");
        return record;
    }
}
