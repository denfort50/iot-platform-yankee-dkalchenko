package ru.dkalchenko.collector.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import ru.dkalchenko.collector.assistant.ThroughputMeter;
import ru.dkalchenko.collector.metrics.CollectorMetrics;
import ru.dkalchenko.collector.mapper.DeviceEventMapper;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.service.EventService;

/**
 * Потребитель сырых событий из топика events.v1.
 * Получает Avro GenericRecord и парсит в {@link DeviceEvent} — без лишнего конвертера и аллокаций.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventListener {

    EventService eventService;
    ThroughputMeter throughputMeter;
    DeviceEventMapper deviceEventMapper;
    CollectorMetrics collectorMetrics;

    @KafkaListener(topics = "${collector.listener.kafka-topic:events.v1}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleRawEvent(
            @Payload GenericRecord raw,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        DeviceEvent event = deviceEventMapper.fromGenericRecord(raw);
        try {
            log.debug("Received raw event: key={}, partition={}, offset={}, eventId={}, deviceId={}, type={}",
                    key, partition, offset, event.eventId(), event.deviceId(), event.type());
            eventService.process(event);
            acknowledgment.acknowledge();
            throughputMeter.record();
            collectorMetrics.incrementEventsProcessed();
        } catch (Exception e) {
            log.error("Error processing event: key={}, partition={}, offset={}", 
                    key, partition, offset, e);
            acknowledgment.acknowledge();
        }
    }
}
