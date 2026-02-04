package ru.dkalchenko.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dkalchenko.collector.metrics.CollectorMetrics;
import ru.dkalchenko.collector.mapper.DeviceOutboxMapper;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.repository.EventRepository;
import ru.dkalchenko.collector.repository.OutboxRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventService {

    EventRepository eventRepository;
    OutboxRepository outboxRepository;
    DeduplicationService deduplicationService;
    DeviceOutboxMapper deviceOutboxMapper;
    CollectorMetrics collectorMetrics;

    /**
     * Обрабатывает событие: сохраняет в ClickHouse; в outbox пишет только при новом device_id за сутки (devices:seen).
     * Метрики:
     * - дубликаты считаются здесь (events.duplicates.total);
     * - общее число обработанных событий считается на уровне listener'а (events.processed.total).
     */
    public void process(DeviceEvent event) {
        eventRepository.save(event);

        if (!deduplicationService.isNewDevice(event.deviceId())) {
            collectorMetrics.incrementEventsDuplicate();
            return;
        }

        DeviceOutbox outbox = deviceOutboxMapper.toOutbox(event);
        outboxRepository.save(outbox);
        log.debug("New device, outbox saved: deviceId={}", event.deviceId());
    }
}
