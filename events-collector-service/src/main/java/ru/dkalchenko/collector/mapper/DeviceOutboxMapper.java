package ru.dkalchenko.collector.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.model.DeviceOutbox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * MapStruct-маппер: DeviceEvent → DeviceOutbox.
 * Поле createdAt заполняется из event.timestamp() (epoch ms → LocalDateTime).
 */
@Mapper(componentModel = "spring")
public interface DeviceOutboxMapper {

    @Mapping(target = "createdAt", source = "timestamp", qualifiedByName = "timestampToLocalDateTime")
    @Mapping(target = "attempts", constant = "0")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "traceId", ignore = true)
    @Mapping(target = "spanId", ignore = true)
    DeviceOutbox toOutbox(DeviceEvent event);

    @Named("timestampToLocalDateTime")
    default LocalDateTime timestampToLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
    }
}
