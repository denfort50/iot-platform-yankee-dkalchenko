package ru.dkalchenko.collector.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Клиент для работы с Redis (дедупликация, регистр инстансов и т.д.).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisClient {

    /**
     * Lua-скрипт: SADD и EXPIRE только если у ключа ещё нет TTL (ставим один раз, не обновляем).
     */
    static String SADD_WITH_EXPIRE_LUA = """
            local added = redis.call('SADD', KEYS[1], ARGV[1])
            if added == 1 then
              local ttl = redis.call('TTL', KEYS[1])
              if ttl == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
              end
            end
            return added
            """;
    static DefaultRedisScript<Long> SADD_WITH_EXPIRE_SCRIPT =
            new DefaultRedisScript<>(SADD_WITH_EXPIRE_LUA, Long.class);

    StringRedisTemplate redisTemplate;

    /**
     * Атомарно: SADD key member; EXPIRE key ttlSeconds вызывается только если у ключа нет TTL (TTL == -1).
     * TTL задаётся один раз при первом появлении ключа и не обновляется при последующих SADD.
     *
     * @param key        ключ множества
     * @param ttlSeconds TTL в секундах (применяется только при первом EXPIRE)
     * @param member     элемент для добавления
     * @return количество добавленных элементов (1 — новый, 0 — уже был)
     */
    public Long sAddWithExpire(String key, long ttlSeconds, String member) {
        if (member == null || member.isBlank()) {
            return 0L;
        }
        return redisTemplate.execute(SADD_WITH_EXPIRE_SCRIPT, List.of(key), member, String.valueOf(ttlSeconds));
    }

    /**
     * Удаляет элемент из множества (для отката при неуспешной отправке в Kafka).
     *
     * @param key    ключ множества
     * @param member элемент для удаления
     */
    public void sRem(String key, String member) {
        if (member == null || member.isBlank()) {
            return;
        }
        redisTemplate.opsForSet().remove(key, member);
    }
}
