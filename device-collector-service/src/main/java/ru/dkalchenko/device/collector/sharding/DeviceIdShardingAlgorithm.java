package ru.dkalchenko.device.collector.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;

/**
 * Шардинг по device_id (VARCHAR): hashCode % 3 → выбор БД ds0, ds1 или ds2.
 * Во всех шардах одна таблица devices; маршрутизация только по БД.
 */
public final class DeviceIdShardingAlgorithm implements StandardShardingAlgorithm<String> {

	private static final int SHARD_COUNT = 3;

	@Override
	public String doSharding(final Collection<String> availableTargetNames,
			final PreciseShardingValue<String> shardingValue) {
		String deviceId = shardingValue.getValue();
		int index = shardIndex(deviceId);
		String suffix = String.valueOf(index);
		// Для БД: ds0, ds1, ds2 — выбираем по суффиксу
		// Для таблицы: один элемент "devices" — возвращаем как есть
		if (availableTargetNames.size() == 1) {
			return availableTargetNames.iterator().next();
		}
		return availableTargetNames.stream()
				.filter(name -> name.endsWith(suffix) || name.endsWith("_" + suffix))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No target for shard index " + index + ": " + availableTargetNames));
	}

	@Override
	public Collection<String> doSharding(final Collection<String> availableTargetNames,
			final RangeShardingValue<String> shardingValue) {
		return availableTargetNames;
	}

    @Override
	public String getType() {
		return "DEVICE_ID_HASH_MOD";
	}

	public static int shardIndex(String deviceId) {
		if (deviceId == null || deviceId.isBlank()) {
			return 0;
		}
		return Math.abs(deviceId.hashCode() % SHARD_COUNT);
	}
}
