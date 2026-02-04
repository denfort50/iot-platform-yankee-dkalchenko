package ru.dkalchenko.collector.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.dkalchenko.collector.client.RedisClient;
import ru.dkalchenko.collector.mapper.DeviceDtoAvroSchema;
import ru.dkalchenko.collector.metrics.CollectorMetrics;
import ru.dkalchenko.collector.model.DeviceOutbox;
import ru.dkalchenko.collector.repository.OutboxRepository;
import ru.dkalchenko.collector.properties.OutboxProperties;

import java.util.List;

/**
 * Фоновый процесс: по расписанию забирает из device_outbox записи для своего шарда,
 * идемпотентность через devices:published, отправляет в Kafka, обновляет статус.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxProcessor {

    OutboxRepository outboxRepository;
    InstanceRegistry instanceRegistry;
    RedisClient redisClient;
    DevicePublisher devicePublisher;
    DeviceDtoAvroSchema deviceDtoAvroSchema;
    OutboxProperties outboxProperties;
    CollectorMetrics collectorMetrics;

    /**
     * Запуск по расписанию (по умолчанию раз в минуту):
     * забирает по batchSize записей для своего шарда, обрабатывает (идемпотентность SADD, Kafka, markAsSent),
     * повторяет пока таблица outbox для шарда не опустеет.
     */
    @Scheduled(cron = "${collector.outbox.schedule:0 * * * * *}")
    public void processOutbox() {
        int total = instanceRegistry.getTotalInstances();
        int myIndex = instanceRegistry.getMyIndex(); // 1..total, 0 если инстанс не в списке
        if (myIndex == 0) {
            log.trace("Outbox: instance not in registry, skipping");
            return;
        }
        int limit = outboxProperties.batchSize();
        long ttl = outboxProperties.publishedTtlSeconds();
        int totalProcessed = 0;
        List<DeviceOutbox> pending;
        while (!(pending = outboxRepository.findPendingForShard(total, myIndex, limit)).isEmpty()) {
            log.debug("Outbox shard {}/{}: processing batch of {} records", myIndex, total, pending.size());
            for (DeviceOutbox row : pending) {
                if (processOne(row, ttl)) {
                    totalProcessed++;
                }
            }
        }
        if (totalProcessed > 0) {
            log.info("Outbox shard {}/{}: finished, processed {} records in total", myIndex, total, totalProcessed);
        } else {
            log.debug("Outbox shard {}/{}: no pending records", myIndex, total);
        }
    }

    /**
     * Обрабатывает одну запись outbox. Возвращает true, если сообщение было отправлено в Kafka.
     */
    private boolean processOne(DeviceOutbox row, long ttlSeconds) {
        String deviceId = row.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }

        String publishedKey = outboxProperties.publishedKey();
        // Идемпотентность — отправляем только если SADD вернул 1 (ещё не публиковали в Kafka)
        Long added = redisClient.sAddWithExpire(publishedKey, ttlSeconds, deviceId);
        if (added == null || added == 0) {
            log.debug("Skip, already published ({}): deviceId={}", publishedKey, deviceId);
            return false;
        }
        GenericRecord record = deviceDtoAvroSchema.createRecord(deviceId);
        try {
            devicePublisher.publish(record);
        } catch (Exception e) {
            log.warn("Failed to publish device {}, will retry later: {}", deviceId, e.getMessage());
            redisClient.sRem(publishedKey, deviceId);
            try {
                outboxRepository.incrementAttempts(deviceId, e.getMessage());
            } catch (Exception ex) {
                log.warn("Failed to increment attempts for device {}: {}", deviceId, ex.getMessage());
            }
            collectorMetrics.incrementOutboxPublishFail();
            return false;
        }
        try {
            outboxRepository.markAsSent(deviceId);
        } catch (Exception e) {
            log.warn("Published device {} to Kafka but markAsSent failed (device stays in {}): {}", deviceId, publishedKey, e.getMessage());
            // Не делаем sRem: устройство уже в Kafka, в Redis оставляем, чтобы не слать дубликат при следующем запуске
        }
        collectorMetrics.incrementOutboxPublishSuccess();
        return true;
    }
}
