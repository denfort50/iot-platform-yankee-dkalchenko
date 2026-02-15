package ru.dkalchenko.device.collector.unit.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.device.collector.sharding.DeviceIdShardingAlgorithm;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceIdShardingAlgorithmTest {

    static final Collection<String> DATA_SOURCES = List.of("ds0", "ds1", "ds2");

    DeviceIdShardingAlgorithm algorithm;

    @Mock
    PreciseShardingValue<String> preciseShardingValue;

    @BeforeEach
    void setUp() {
        algorithm = new DeviceIdShardingAlgorithm();
        algorithm.init(new java.util.Properties());
    }

    @Test
    void getTypeReturnsExpectedAlgorithmName() {
        assertThat(algorithm.getType()).isEqualTo("DEVICE_ID_HASH_MOD");
    }

    @Test
    void whenSameDeviceIdThenSameShardIndex() {
        String deviceId = "device-001";
        int index1 = DeviceIdShardingAlgorithm.shardIndex(deviceId);
        int index2 = DeviceIdShardingAlgorithm.shardIndex(deviceId);
        assertThat(index1).isEqualTo(index2);
        assertThat(index1).isBetween(0, 2);
    }

    @Test
    void whenDeviceIdThenShardIndexMatchesHashCodeModThree() {
        String deviceId = "device-xyz";
        int expectedIndex = Math.abs(deviceId.hashCode() % 3);
        assertThat(DeviceIdShardingAlgorithm.shardIndex(deviceId)).isEqualTo(expectedIndex);
    }

    @Test
    void whenNullDeviceIdThenShardIndexZero() {
        assertThat(DeviceIdShardingAlgorithm.shardIndex(null)).isEqualTo(0);
    }

    @Test
    void whenBlankDeviceIdThenShardIndexZero() {
        assertThat(DeviceIdShardingAlgorithm.shardIndex("")).isEqualTo(0);
        assertThat(DeviceIdShardingAlgorithm.shardIndex("   ")).isEqualTo(0);
    }

    @Test
    void whenDoShardingWithPreciseValueThenReturnsCorrectDataSource() {
        String deviceId = "device-001";
        int expectedIndex = DeviceIdShardingAlgorithm.shardIndex(deviceId);
        String expectedDs = "ds" + expectedIndex;
        when(preciseShardingValue.getValue()).thenReturn(deviceId);

        String result = algorithm.doSharding(DATA_SOURCES, preciseShardingValue);

        assertThat(result).isEqualTo(expectedDs);
    }

    @Test
    void whenSameDeviceIdThenDoShardingReturnsSameShard() {
        String deviceId = "device-idempotent";
        when(preciseShardingValue.getValue()).thenReturn(deviceId);

        String result1 = algorithm.doSharding(DATA_SOURCES, preciseShardingValue);
        String result2 = algorithm.doSharding(DATA_SOURCES, preciseShardingValue);

        assertThat(result1).isEqualTo(result2);
        assertThat(DATA_SOURCES).contains(result1);
    }

    @Test
    void whenNullDeviceIdThenDoShardingReturnsDs0() {
        when(preciseShardingValue.getValue()).thenReturn(null);

        String result = algorithm.doSharding(DATA_SOURCES, preciseShardingValue);

        assertThat(result).isEqualTo("ds0");
    }

    @Test
    void whenSingleTargetNameThenReturnsThatTarget() {
        Collection<String> single = List.of("devices");
        when(preciseShardingValue.getValue()).thenReturn("device-001");

        String result = algorithm.doSharding(single, preciseShardingValue);

        assertThat(result).isEqualTo("devices");
    }

    @Test
    void whenTargetNamesWithSuffixFormatThenSelectsByShardIndex() {
        // Алгоритм ищет имя, оканчивающееся на suffix или "_" + suffix (0, 1, 2)
        Collection<String> withSuffix = List.of("node_0", "node_1", "node_2");
        String deviceId = "device-001";
        int expectedIndex = DeviceIdShardingAlgorithm.shardIndex(deviceId);
        when(preciseShardingValue.getValue()).thenReturn(deviceId);

        String result = algorithm.doSharding(withSuffix, preciseShardingValue);

        assertThat(result).isEqualTo("node_" + expectedIndex);
        assertThat(result).isIn(withSuffix);
    }

    @Test
    void whenNoMatchingTargetThenThrows() {
        Collection<String> noMatch = List.of("other_a", "other_b");
        when(preciseShardingValue.getValue()).thenReturn("device-001");

        assertThatThrownBy(() -> algorithm.doSharding(noMatch, preciseShardingValue))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No target for shard index");
    }

    @Test
    void rangeShardingReturnsAllTargets() {
        @SuppressWarnings("unchecked")
        RangeShardingValue<String> rangeValue = org.mockito.Mockito.mock(RangeShardingValue.class);

        Collection<String> result = algorithm.doSharding(DATA_SOURCES, rangeValue);

        assertThat(result).containsExactlyInAnyOrderElementsOf(DATA_SOURCES);
    }
}
