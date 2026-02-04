package ru.dkalchenko.collector.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.dkalchenko.collector.client.RedisClient;
import ru.dkalchenko.collector.service.DeduplicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    static final String SEEN_KEY = "devices:seen";
    static final int SEEN_TTL = 86400;

    @Mock
    RedisClient redisClient;

    @InjectMocks
    DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deduplicationService, "seenKey", SEEN_KEY);
        ReflectionTestUtils.setField(deduplicationService, "seenTtlSeconds", SEEN_TTL);
    }

    @Test
    void whenNewDeviceIdThenCreatesOutboxPath() {
        String deviceId = "device-001";
        when(redisClient.sAddWithExpire(eq(SEEN_KEY), eq((long) SEEN_TTL), eq(deviceId))).thenReturn(1L);

        boolean result = deduplicationService.isNewDevice(deviceId);

        assertThat(result).isTrue();
        verify(redisClient).sAddWithExpire(SEEN_KEY, SEEN_TTL, deviceId);
    }

    @Test
    void whenExistingDeviceIdThenNoOutboxCreated() {
        String deviceId = "device-002";
        when(redisClient.sAddWithExpire(eq(SEEN_KEY), eq((long) SEEN_TTL), eq(deviceId))).thenReturn(0L);

        boolean result = deduplicationService.isNewDevice(deviceId);

        assertThat(result).isFalse();
        verify(redisClient).sAddWithExpire(SEEN_KEY, SEEN_TTL, deviceId);
    }

    @Test
    void whenRedisReturnsNullThenTreatedAsDuplicate() {
        when(redisClient.sAddWithExpire(eq(SEEN_KEY), anyLong(), eq("device-003"))).thenReturn(null);

        boolean result = deduplicationService.isNewDevice("device-003");

        assertThat(result).isFalse();
    }
}
