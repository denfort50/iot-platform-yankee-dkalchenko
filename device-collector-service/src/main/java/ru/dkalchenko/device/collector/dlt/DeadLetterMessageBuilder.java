package ru.dkalchenko.device.collector.dlt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.stereotype.Component;

/**
 * Строит Avro-запись DeadLetterMessage по схеме DeadLetterMessage.avsc.
 * Единственная ответственность: преобразование неудачного ConsumerRecord и исключения
 * в GenericRecord для отправки в DLT.
 */
@Component
public class DeadLetterMessageBuilder {

    private static final String SERVICE_NAME = "device-collector-service";
    private static final Schema DLT_MESSAGE_SCHEMA = loadDeadLetterMessageSchema();
    private static final ObjectMapper PLAIN_JSON_MAPPER = new ObjectMapper();

    /**
     * Собирает Avro-запись DeadLetterMessage.
     */
    public GenericRecord build(ConsumerRecord<?, ?> record, Exception ex) {
        GenericRecord msg = new GenericData.Record(DLT_MESSAGE_SCHEMA);
        msg.put("id", UUID.randomUUID().toString());
        msg.put("service", SERVICE_NAME);
        msg.put("originalTopic", record.topic());
        msg.put("timestamp", System.currentTimeMillis());
        msg.put("errorMessage", ex.getMessage());
        msg.put("stackTrace", Arrays.toString(ex.getStackTrace()));
        msg.put("traceId", getTraceIdFromRecord(record));
        msg.put("originalKey", record.key() != null ? record.key().toString() : null);
        msg.put("originalValue", originalValueToJson(record.value()));
        return msg;
    }

    /**
     * Читает traceId из заголовков сообщения.
     */
    private static String getTraceIdFromRecord(ConsumerRecord<?, ?> record) {
        String traceId = "";
        if (record != null && record.headers() != null) {
            Header traceIdHeader = record.headers().lastHeader("trace_id");
            if (traceIdHeader != null && traceIdHeader.value() != null) {
                String t = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
                if (!t.isBlank()) traceId = t;
            }
        }
        return traceId;
    }

    private static String originalValueToJson(Object value) {
        switch (value) {
            case null -> {
                return "";
            }
            case GenericRecord genericRecord -> {
                try {
                    Map<String, Object> plain = genericRecordToPlainMap(genericRecord);
                    return PLAIN_JSON_MAPPER.writeValueAsString(plain);
                } catch (JsonProcessingException e) {
                    return "{\"serializationError\":\"" + escapeJson(e.getMessage()) + "\"}";
                }
            }
            case byte[] bytes -> {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            default -> {
            }
        }
        return value.toString();
    }

    /** Сериализация GenericRecord в «чистый» JSON без Avro-обёрток union ({"string":"v"} → "v"). */
    private static Map<String, Object> genericRecordToPlainMap(GenericRecord record) {
        return record.getSchema().getFields().stream()
                .collect(LinkedHashMap::new,
                        (map, field) -> map.put(field.name(),
                                avroValueToPlainObject(record.get(field.name()), field.schema())),
                        Map::putAll);
    }

    private static Object avroValueToPlainObject(Object value, Schema schema) {
        if (value == null) return null;

        return switch (schema.getType()) {
            case UNION -> extractFromUnion(value, schema);
            case RECORD -> genericRecordToPlainMap((GenericRecord) value);
            case ARRAY -> ((List<?>) value).stream()
                    .map(item -> avroValueToPlainObject(item, schema.getElementType()))
                    .toList();
            case MAP -> ((Map<?, ?>) value).entrySet().stream()
                    .collect(LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey().toString(),
                                    avroValueToPlainObject(entry.getValue(), schema.getValueType())),
                            Map::putAll);
            case STRING, ENUM -> value.toString();
            case LONG, INT, FLOAT, DOUBLE, BOOLEAN -> value;
            default -> value.toString();
        };
    }

    private static Object extractFromUnion(Object value, Schema schema) {
        return schema.getTypes().stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .findFirst()
                .map(s -> avroValueToPlainObject(value, s))
                .orElse(null);
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Schema loadDeadLetterMessageSchema() {
        try {
            return new Schema.Parser().parse(
                    StreamUtils.copyToString(
                            new ClassPathResource("avro/DeadLetterMessage.avsc").getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load DeadLetterMessage.avsc", e);
        }
    }
}
