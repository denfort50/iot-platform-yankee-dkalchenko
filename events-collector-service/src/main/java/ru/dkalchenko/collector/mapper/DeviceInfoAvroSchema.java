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
 * Загружает Avro-схему DeviceInfo из classpath (avro/DeviceInfo.avsc) и строит GenericRecord по deviceId.
 */
@Getter
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceInfoAvroSchema {

    static String DEVICE_INFO_SCHEMA_PATH = "avro/DeviceInfo.avsc";

    Schema schema;

    public DeviceInfoAvroSchema() throws IOException {
        try (InputStream in = new ClassPathResource(DEVICE_INFO_SCHEMA_PATH).getInputStream()) {
            String schemaJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            this.schema = new Schema.Parser().parse(schemaJson);
        }
    }

    /**
     * Строит GenericRecord по схеме DeviceInfo для отправки в Kafka
     * на основе записи из outbox.
     */
    public GenericRecord createRecord(ru.dkalchenko.collector.model.DeviceOutbox outbox) {
        GenericRecord record = new GenericData.Record(schema);
        String deviceId = outbox.deviceId();
        String deviceType = outbox.deviceType();
        Long createdAt = outbox.createdAt();
        String meta = outbox.meta();

        record.put("deviceId", deviceId != null ? deviceId : "");
        record.put("deviceType", deviceType != null ? deviceType : "");
        record.put("createdAt", createdAt != null ? createdAt : System.currentTimeMillis());
        record.put("meta", meta != null ? meta : "{}");
        return record;
    }
}
