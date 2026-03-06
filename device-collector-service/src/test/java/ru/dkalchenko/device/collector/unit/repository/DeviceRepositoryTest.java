package ru.dkalchenko.device.collector.unit.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dkalchenko.device.collector.repository.DeviceRepository;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты репозитория: проверка идемпотентности на уровне DAO —
 * один и тот же device_id при повторном upsert приводит к UPDATE (один вызов update с теми же параметрами).
 */
@ExtendWith(MockitoExtension.class)
class DeviceRepositoryTest {

    static final String DEVICE_ID = "device-idempotent";
    static final String DEVICE_TYPE = "sensor";
    static final long CREATED_AT = 1_700_000_000_000L;
    static final String META = "{\"x\":1}";

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    DeviceRepository deviceRepository;

    @Test
    void whenUpsertThenExecutesUpdateWithCorrectParams() {
        Instant updatedAt = Instant.now();
        when(jdbcTemplate.update(
                isA(String.class),
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                isA(Timestamp.class)
        )).thenReturn(1);

        int result = deviceRepository.upsertDevice(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, updatedAt);

        assertThat(result).isEqualTo(1);
        ArgumentCaptor<Timestamp> tsCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbcTemplate).update(
                isA(String.class),
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                tsCaptor.capture()
        );
        assertThat(tsCaptor.getValue().toInstant()).isEqualTo(updatedAt);
    }

    @Test
    void whenSameDeviceUpsertedTwiceThenSameSqlCalledTwiceIdempotent() {
        Instant t1 = Instant.now();
        Instant t2 = t1.plusSeconds(10);
        when(jdbcTemplate.update(
                isA(String.class),
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                isA(Timestamp.class)
        )).thenReturn(1).thenReturn(1);

        deviceRepository.upsertDevice(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, t1);
        deviceRepository.upsertDevice(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META, t2);

        verify(jdbcTemplate, times(2)).update(
                isA(String.class),
                eq(DEVICE_ID),
                eq(DEVICE_TYPE),
                eq(CREATED_AT),
                eq(META),
                isA(Timestamp.class)
        );
    }

    @Test
    void whenUpdateThenExecutesUpdateWithCorrectParams() {
        Instant updatedAt = Instant.now();
        when(jdbcTemplate.update(
                isA(String.class),
                eq(DEVICE_TYPE),
                eq(META),
                isA(Timestamp.class),
                eq(DEVICE_ID)
        )).thenReturn(1);

        int result = deviceRepository.updateDevice(DEVICE_ID, DEVICE_TYPE, META, updatedAt);

        assertThat(result).isEqualTo(1);
        verify(jdbcTemplate).update(
                isA(String.class),
                eq(DEVICE_TYPE),
                eq(META),
                isA(Timestamp.class),
                eq(DEVICE_ID)
        );
    }

    @Test
    void whenDeleteByDeviceIdThenExecutesDelete() {
        when(jdbcTemplate.update(isA(String.class), eq(DEVICE_ID))).thenReturn(1);

        int result = deviceRepository.deleteByDeviceId(DEVICE_ID);

        assertThat(result).isEqualTo(1);
        verify(jdbcTemplate).update(isA(String.class), eq(DEVICE_ID));
    }
}
