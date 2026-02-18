package ru.dkalchenko.device.collector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.dkalchenko.device.collector.properties.DeviceCollectorProperties;

/**
 * Набор кастомных метрик для device-collector-service.
 * Счётчики успешно/неуспешно обработанных сообщений и отправок в DLT с разбивкой по шардам (тег {@code shard}).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CollectorMetrics {

    static final String TAG_SHARD = "shard";
    static final String SHARD_UNKNOWN = "unknown";

    MeterRegistry meterRegistry;
    int shardingCount;

    public CollectorMetrics(MeterRegistry meterRegistry, DeviceCollectorProperties properties) {
        this.meterRegistry = meterRegistry;
        this.shardingCount = Math.max(1, properties.getSharding().getShardingCount());
    }

    /**
     * Номер шарда по device_id (тот же расчёт, что и в ShardingSphere HASH_MOD).
     */
    public int getShardIndex(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return -1;
        }
        return Math.floorMod(deviceId.hashCode(), shardingCount);
    }

    /**
     * Значение тега shard для метрик: "0", "1", "2" или "unknown".
     */
    public String getShardTag(String deviceId) {
        int idx = getShardIndex(deviceId);
        return idx >= 0 ? String.valueOf(idx) : SHARD_UNKNOWN;
    }

    private Counter processedCounter(String shardTag) {
        return Counter.builder("device.collector.processed.total")
                .description("Total number of device messages processed successfully (by shard)")
                .tag(TAG_SHARD, shardTag)
                .register(meterRegistry);
    }

    private Counter failedCounter(String shardTag) {
        return Counter.builder("device.collector.failed.total")
                .description("Total number of device messages that failed to process / errors by shard")
                .tag(TAG_SHARD, shardTag)
                .register(meterRegistry);
    }

    private Counter dltSentCounter(String shardTag) {
        return Counter.builder("device.collector.dlt.sent.total")
                .description("Total number of device messages sent to Dead Letter Topic (by shard)")
                .tag(TAG_SHARD, shardTag)
                .register(meterRegistry);
    }

    /**
     * Успешно обработано (с разбивкой по шарду).
     */
    public void incrementDevicesProcessed(String deviceId) {
        processedCounter(getShardTag(deviceId)).increment();
    }

    /**
     * Ошибка обработки (с разбивкой по шарду, если deviceId известен).
     */
    public void incrementDevicesFailed(String deviceId) {
        failedCounter(getShardTag(deviceId)).increment();
    }

    /**
     * Отправлено в DLT (с разбивкой по шарду, если deviceId известен).
     */
    public void incrementDevicesSentToDlt(String deviceId) {
        dltSentCounter(getShardTag(deviceId)).increment();
    }
}
