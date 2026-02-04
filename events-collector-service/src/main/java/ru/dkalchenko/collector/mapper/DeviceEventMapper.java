package ru.dkalchenko.collector.mapper;

import org.apache.avro.generic.GenericRecord;
import org.mapstruct.Mapper;
import ru.dkalchenko.collector.model.DeviceEvent;

import java.util.Objects;

/**
 * MapStruct-маппер: Avro GenericRecord → DeviceEvent.
 */
@Mapper(componentModel = "spring")
public interface DeviceEventMapper {

    default DeviceEvent fromGenericRecord(GenericRecord record) {
        if (record == null) {
            return null;
        }
        return new DeviceEvent(
                getString(record, "eventId"),
                getString(record, "deviceId"),
                getLong(record, "timestamp"),
                getString(record, "type"),
                getString(record, "payload")
        );
    }

    default String getString(GenericRecord record, String field) {
        Object value = record.get(field);
        return value == null ? null : value.toString();
    }

    default Long getLong(GenericRecord record, String field) {
        Object value = record.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(Objects.toString(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
