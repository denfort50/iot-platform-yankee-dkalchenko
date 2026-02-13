package ru.dkalchenko.collector.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import ru.dkalchenko.collector.tracing.TraceContextCapture;

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
    Tracer tracer;

    /**
     * Обрабатывает событие: сохраняет в ClickHouse; в outbox пишет только при новом device_id за сутки (devices:seen).
     * Контекст корня (process-event) сохраняется в outbox, чтобы publish-device-kafka был дочерним тому же корню.
     * Иерархия в Tempo: process-event → (save-event-clickhouse, deduplication-redis, save-device-outbox); process-event → publish-device-kafka.
     */
    public void process(DeviceEvent event) {
        Span processSpan = tracer.nextSpan().name("process-event")
                .tag("device.id", event.deviceId() != null ? event.deviceId() : "")
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(processSpan)) {
            runProcessWithSingleTrace(event);
        } finally {
            processSpan.end();
        }
    }

    private void runProcessWithSingleTrace(DeviceEvent event) {
        String rootTraceId = TraceContextCapture.getCurrentTraceId(tracer);
        String rootSpanId = TraceContextCapture.getCurrentSpanId(tracer);

        Span saveSpan = tracer.nextSpan().name("save-event-clickhouse")
                .tag("event.id", event.eventId() != null ? event.eventId() : "")
                .tag("device.id", event.deviceId() != null ? event.deviceId() : "")
                .tag("db.name", "clickhouse")
                .tag("db.operation", "insert")
                .start();
        try (Tracer.SpanInScope _ = tracer.withSpan(saveSpan)) {
            eventRepository.save(event);
        } finally {
            saveSpan.end();
        }

        Span dedupSpan = tracer.nextSpan().name("deduplication-redis").start();
        boolean isNewDevice;
        try (Tracer.SpanInScope _ = tracer.withSpan(dedupSpan)) {
            isNewDevice = deduplicationService.isNewDevice(event.deviceId());
            if (!isNewDevice) {
                collectorMetrics.incrementEventsDuplicate();
                return;
            }
        } finally {
            dedupSpan.end();
        }

        Span outboxSaveSpan = tracer.nextSpan().name("save-device-outbox").start();
        try (Tracer.SpanInScope _ = tracer.withSpan(outboxSaveSpan)) {
            DeviceOutbox base = deviceOutboxMapper.toOutbox(event);
            DeviceOutbox deviceOutbox = new DeviceOutbox(base, rootTraceId, rootSpanId);
            outboxRepository.save(deviceOutbox);
        } finally {
            outboxSaveSpan.end();
        }
        log.debug("New device, deviceOutbox saved: deviceId={}", event.deviceId());
    }
}
