package ru.dkalchenko.collector.unit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.collector.metrics.CollectorMetrics;
import ru.dkalchenko.collector.mapper.DeviceOutboxMapper;
import ru.dkalchenko.collector.model.DeviceEvent;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.repository.EventRepository;
import ru.dkalchenko.collector.repository.OutboxRepository;
import ru.dkalchenko.collector.service.DeduplicationService;
import ru.dkalchenko.collector.service.EventService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    static final String DEVICE_ID = "device-001";
    static final DeviceEvent EVENT = new DeviceEvent(
            "ev-1", DEVICE_ID, System.currentTimeMillis(), "temperature", "{}");

    @Mock
    EventRepository eventRepository;
    @Mock
    OutboxRepository outboxRepository;
    @Mock
    DeduplicationService deduplicationService;
    @Mock
    DeviceOutboxMapper deviceOutboxMapper;
    @Mock
    CollectorMetrics collectorMetrics;
    @Mock
    Tracer tracer;
    @Mock
    Span span;
    @Mock
    Span saveSpanBuilder;
    @Mock
    Tracer.SpanInScope spanInScope;
    @Mock
    io.micrometer.tracing.TraceContext traceContext;

    @InjectMocks
    EventService eventService;

    private void stubTracerForSaveSpan() {
        when(saveSpanBuilder.name(anyString())).thenReturn(saveSpanBuilder);
        when(saveSpanBuilder.tag(anyString(), anyString())).thenReturn(saveSpanBuilder);
        when(saveSpanBuilder.start()).thenReturn(span);
        when(tracer.nextSpan()).thenReturn(saveSpanBuilder);
        when(tracer.withSpan(any(Span.class))).thenReturn(spanInScope);
    }

    @Test
    void whenNewDeviceIdThenCreatesOutboxRecordWithTraceContext() {
        stubTracerForSaveSpan();
        LocalDateTime now = LocalDateTime.now();
        DeviceOutbox baseOutbox = new DeviceOutbox(DEVICE_ID, now, 0, null, null, null);
        when(deduplicationService.isNewDevice(DEVICE_ID)).thenReturn(true);
        when(deviceOutboxMapper.toOutbox(EVENT)).thenReturn(baseOutbox);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("abc123");
        when(traceContext.spanId()).thenReturn("def456");

        eventService.process(EVENT);

        verify(eventRepository).save(EVENT);
        verify(deduplicationService).isNewDevice(DEVICE_ID);
        verify(deviceOutboxMapper).toOutbox(EVENT);
        ArgumentCaptor<DeviceOutbox> captor = ArgumentCaptor.forClass(DeviceOutbox.class);
        verify(outboxRepository).save(captor.capture());
        DeviceOutbox saved = captor.getValue();
        assertThat(saved.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.traceId()).isEqualTo("abc123");
        assertThat(saved.spanId()).isEqualTo("def456");
        verify(collectorMetrics, never()).incrementEventsDuplicate();
    }

    @Test
    void whenNewDeviceIdAndNoCurrentSpanThenSavesOutboxWithNullTraceContext() {
        stubTracerForSaveSpan();
        LocalDateTime now = LocalDateTime.now();
        DeviceOutbox baseOutbox = new DeviceOutbox(DEVICE_ID, now, 0, null, null, null);
        when(deduplicationService.isNewDevice(DEVICE_ID)).thenReturn(true);
        when(deviceOutboxMapper.toOutbox(EVENT)).thenReturn(baseOutbox);
        when(tracer.currentSpan()).thenReturn(null);

        eventService.process(EVENT);

        ArgumentCaptor<DeviceOutbox> captor = ArgumentCaptor.forClass(DeviceOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().traceId()).isNull();
        assertThat(captor.getValue().spanId()).isNull();
    }

    @Test
    void whenExistingDeviceIdThenDoesNotCreateOutboxRecord() {
        stubTracerForSaveSpan();
        when(deduplicationService.isNewDevice(DEVICE_ID)).thenReturn(false);

        eventService.process(EVENT);

        verify(eventRepository).save(EVENT);
        verify(deduplicationService).isNewDevice(DEVICE_ID);
        verify(deviceOutboxMapper, never()).toOutbox(EVENT);
        verify(outboxRepository, never()).save(any());
        verify(collectorMetrics).incrementEventsDuplicate();
    }
}
