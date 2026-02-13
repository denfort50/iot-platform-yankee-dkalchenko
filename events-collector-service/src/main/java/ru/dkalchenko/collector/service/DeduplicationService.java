package ru.dkalchenko.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.dkalchenko.collector.client.RedisClient;

/**
 * Дедупликация device_id через Redis (множество devices:seen).
 * SADD атомарен — при нескольких экземплярах сервиса гонки не возникает.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeduplicationService {

    final RedisClient redisClient;

    @Value("${collector.deduplication.seen-key:devices:seen}")
    String seenKey;

    @Value("${collector.deduplication.seen-ttl-seconds:86400}")
    int seenTtlSeconds;

    /**
     * Проверяет, встречался ли уже этот device_id (в пределах TTL ключа).
     * Выполняет SADD по ключу seenKey; TTL ключа задаётся один раз при первом добавлении и не обновляется.
     *
     * @param deviceId идентификатор устройства
     * @return true — новый device (SADD вернул 1), false — дубликат (SADD вернул 0)
     */
    public boolean isNewDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Empty deviceId treated as duplicate");
            return true;
        }
        Long added = redisClient.sAddWithExpire(seenKey, seenTtlSeconds, deviceId);
        boolean duplicated = added == null || added == 0;
        if (log.isTraceEnabled()) {
            log.trace("deviceId = {}, SADD added = {}, duplicated = {}", deviceId, added, duplicated);
        }
        return !duplicated;
    }
}
