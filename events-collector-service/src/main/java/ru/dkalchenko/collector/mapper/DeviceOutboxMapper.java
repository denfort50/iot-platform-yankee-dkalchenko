package ru.dkalchenko.collector.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.model.DeviceOutbox;

/**
 * MapStruct-маппер: DeviceEvent → DeviceOutbox.
 * Поля:
 * - deviceType заполняется из event.type()
 * - createdAt заполняется из event.timestamp() (epoch ms)
 * - meta заполняется из event.payload()
 */
@Mapper(componentModel = "spring")
public interface DeviceOutboxMapper {

    @Mapping(target = "operation", constant = "CREATE")
    @Mapping(target = "deviceType", source = "type")
    @Mapping(target = "createdAt", source = "timestamp")
    @Mapping(target = "meta", source = "payload")
    @Mapping(target = "attempts", constant = "0")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "traceId", ignore = true)
    @Mapping(target = "spanId", ignore = true)
    DeviceOutbox toOutbox(DeviceEvent event);
}
