package ru.dkalchenko.device.collector.unit.service;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.device.collector.mapper.DeviceMapper;
import ru.dkalchenko.device.collector.model.Device;
import ru.dkalchenko.device.collector.repository.DeviceRepository;
import ru.dkalchenko.device.collector.service.DeviceService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты сервиса устройств: идемпотентность upsert (повторный вызов с тем же device_id
 * не создаёт дубликат, а обновляет запись через DAO).
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    static final String DEVICE_ID = "device-001";
    static final String DEVICE_TYPE = "sensor";
    static final long CREATED_AT = 1_700_000_000_000L;
    static final String META = "{}";

    Schema deviceSchema;
    GenericRecord avroRecord;

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    DeviceMapper deviceMapper;

    @InjectMocks
    DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceSchema = new Schema.Parser().parse("""
            {"type":"record","name":"DeviceDto","namespace":"com.nashkod.avro",
             "fields":[
               {"name":"operation","type":"string"},
               {"name":"deviceId","type":"string"},
               {"name":"deviceType","type":"string"},
               {"name":"createdAt","type":"long"},
               {"name":"meta","type":"string"}
             ]}
            """);
        avroRecord = new GenericData.Record(deviceSchema);
        avroRecord.put("operation", "CREATE");
        avroRecord.put("deviceId", DEVICE_ID);
        avroRecord.put("deviceType", DEVICE_TYPE);
        avroRecord.put("createdAt", CREATED_AT);
        avroRecord.put("meta", META);
    }

    @Test
    void whenCreateThenCallsUpsertWithMappedDevice() {
        Instant now = Instant.now();
        Device device = new Device(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, now);
        when(deviceMapper.getOperation(avroRecord)).thenReturn("CREATE");
        when(deviceMapper.toDevice(avroRecord)).thenReturn(device);
        when(deviceRepository.upsertDevice(eq(DEVICE_ID), eq(DEVICE_TYPE), eq(CREATED_AT), eq(META), any(Instant.class)))
                .thenReturn(1);

        deviceService.processDevice(avroRecord);

        verify(deviceMapper).toDevice(avroRecord);
        ArgumentCaptor<Instant> updatedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(deviceRepository).upsertDevice(
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                updatedAtCaptor.capture()
        );
        assertThat(updatedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void whenUpdateThenCallsUpdateDevice() {
        avroRecord.put("operation", "UPDATE");
        avroRecord.put("createdAt", null);
        ru.dkalchenko.device.collector.model.DeviceUpdate update =
                new ru.dkalchenko.device.collector.model.DeviceUpdate(DEVICE_ID, DEVICE_TYPE, META);
        when(deviceMapper.getOperation(avroRecord)).thenReturn("UPDATE");
        when(deviceMapper.toDeviceForUpdate(avroRecord)).thenReturn(update);
        when(deviceRepository.updateDevice(eq(DEVICE_ID), eq(DEVICE_TYPE), eq(META), any(Instant.class))).thenReturn(1);

        deviceService.processDevice(avroRecord);

        verify(deviceMapper).toDeviceForUpdate(avroRecord);
        verify(deviceRepository).updateDevice(eq(DEVICE_ID), eq(DEVICE_TYPE), eq(META), any(Instant.class));
        verify(deviceRepository, never()).upsertDevice(any(), any(), any(), any(), any());
    }

    @Test
    void whenDeleteThenCallsDeleteByDeviceId() {
        avroRecord.put("operation", "DELETE");
        when(deviceMapper.getOperation(avroRecord)).thenReturn("DELETE");
        when(deviceMapper.getDeviceId(avroRecord)).thenReturn(DEVICE_ID);
        when(deviceRepository.deleteByDeviceId(DEVICE_ID)).thenReturn(1);

        deviceService.processDevice(avroRecord);

        verify(deviceMapper).getDeviceId(avroRecord);
        verify(deviceRepository).deleteByDeviceId(DEVICE_ID);
        verify(deviceRepository, never()).upsertDevice(any(), any(), any(), any(), any());
        verify(deviceRepository, never()).updateDevice(any(), any(), any(), any());
    }

    @Test
    void whenSameDeviceCreateTwiceThenRepositoryCalledTwiceIdempotent() {
        Device device = new Device(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, Instant.now());
        when(deviceMapper.getOperation(avroRecord)).thenReturn("CREATE");
        when(deviceMapper.toDevice(avroRecord)).thenReturn(device);
        when(deviceRepository.upsertDevice(eq(DEVICE_ID), eq(DEVICE_TYPE), eq(CREATED_AT), eq(META), any(Instant.class)))
                .thenReturn(1)
                .thenReturn(1);

        deviceService.processDevice(avroRecord);
        deviceService.processDevice(avroRecord);

        verify(deviceMapper, times(2)).toDevice(avroRecord);
        verify(deviceRepository, times(2)).upsertDevice(
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                any(Instant.class)
        );
    }

    @Test
    void whenMapperThrowsIllegalArgumentThenPropagates() {
        when(deviceMapper.getOperation(avroRecord)).thenReturn("CREATE");
        when(deviceMapper.toDevice(avroRecord)).thenThrow(new IllegalArgumentException("deviceId is required"));

        assertThatThrownBy(() -> deviceService.processDevice(avroRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId is required");

        verify(deviceRepository, never()).upsertDevice(any(), any(), any(), any(), any());
    }

    @Test
    void whenRepositoryReturnsZeroThenNoException() {
        Device device = new Device(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, Instant.now());
        when(deviceMapper.getOperation(avroRecord)).thenReturn("CREATE");
        when(deviceMapper.toDevice(avroRecord)).thenReturn(device);
        when(deviceRepository.upsertDevice(any(), any(), any(), any(), any())).thenReturn(0);

        deviceService.processDevice(avroRecord);

        verify(deviceRepository).upsertDevice(eq(DEVICE_ID), eq(DEVICE_TYPE), eq(CREATED_AT), eq(META), any(Instant.class));
    }
}
