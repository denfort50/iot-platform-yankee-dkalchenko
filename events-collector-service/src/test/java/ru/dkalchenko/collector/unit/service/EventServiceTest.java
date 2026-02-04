package ru.dkalchenko.collector.unit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    EventService eventService;

    @Test
    void whenNewDeviceIdThenCreatesOutboxRecord() {
        DeviceOutbox outbox = new DeviceOutbox(DEVICE_ID, LocalDateTime.now(), 0, null);
        when(deduplicationService.isNewDevice(DEVICE_ID)).thenReturn(true);
        when(deviceOutboxMapper.toOutbox(EVENT)).thenReturn(outbox);

        eventService.process(EVENT);

        verify(eventRepository).save(EVENT);
        verify(deduplicationService).isNewDevice(DEVICE_ID);
        verify(deviceOutboxMapper).toOutbox(EVENT);
        ArgumentCaptor<DeviceOutbox> captor = ArgumentCaptor.forClass(DeviceOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(outbox);
        verify(collectorMetrics, never()).incrementEventsDuplicate();
    }

    @Test
    void whenExistingDeviceIdThenDoesNotCreateOutboxRecord() {
        when(deduplicationService.isNewDevice(DEVICE_ID)).thenReturn(false);

        eventService.process(EVENT);

        verify(eventRepository).save(EVENT);
        verify(deduplicationService).isNewDevice(DEVICE_ID);
        verify(deviceOutboxMapper, never()).toOutbox(EVENT);
        verify(outboxRepository, never()).save(any());
        verify(collectorMetrics).incrementEventsDuplicate();
    }
}
