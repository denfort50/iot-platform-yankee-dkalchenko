package ru.dkalchenko.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Регистрация инстанса в Redis для распределённой работы outbox:
 * каждый инстанс периодически обновляет TTL своего ключа. По множеству ключей
 * вычисляются общее число инстансов и индекс текущего (для шардирования выборки из outbox).
 * Id инстанса генерируется при старте сервиса (UUID).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InstanceRegistry {

    static String INSTANCE_KEY_PREFIX = "collector:instance:";

    final StringRedisTemplate redisTemplate;

    /**
     * Id инстанса, сгенерированный при старте приклада.
     */
    final String instanceId = UUID.randomUUID().toString();

    @Value("${collector.instance.heartbeat-ttl-seconds:90}")
    int heartbeatTtlSeconds;

    String myKey() {
        return INSTANCE_KEY_PREFIX + instanceId;
    }

    /**
     * Продлевает TTL ключа инстанса в Redis (раз в 30 сек по умолчанию).
     */
    @Scheduled(fixedDelayString = "${collector.instance.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        try {
            redisTemplate.opsForValue().set(myKey(), "1", java.time.Duration.ofSeconds(heartbeatTtlSeconds));
            log.trace("Instance heartbeat: {}", instanceId);
        } catch (Exception e) {
            log.warn("Instance heartbeat failed: {}", e.getMessage());
        }
    }

    /**
     * Список id живых инстансов (ключи с не истёкшим TTL).
     */
    public List<String> getLiveInstanceIds() {
        Set<String> keys = redisTemplate.keys(INSTANCE_KEY_PREFIX + "*");
        if (keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(k -> k.substring(INSTANCE_KEY_PREFIX.length()))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Общее число живых инстансов.
     */
    public int getTotalInstances() {
        return getLiveInstanceIds().size();
    }

    /**
     * Номер текущего инстанса среди живых (1..total). Если инстанс не в списке, возвращает 0.
     */
    public int getMyIndex() {
        List<String> ids = getLiveInstanceIds();
        int idx = ids.indexOf(instanceId);
        return idx >= 0 ? idx + 1 : 0;
    }
}
