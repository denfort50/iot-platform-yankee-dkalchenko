package ru.dkalchenko.device.collector.unit.mapper;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.dkalchenko.device.collector.mapper.DeviceMapper;
import ru.dkalchenko.device.collector.model.Device;
import ru.dkalchenko.device.collector.model.DeviceUpdate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты маппера: десериализация Avro GenericRecord в сущность Device.
 */
@ExtendWith(MockitoExtension.class)
class DeviceMapperTest {

    static final String DEVICE_ID = "device-001";
    static final String DEVICE_TYPE = "sensor";
    static final long CREATED_AT = 1_700_000_000_000L;
    static final String META = "{\"location\":\"room1\"}";

    Schema deviceSchema;
    DeviceMapper deviceMapper;

    @BeforeEach
    void setUp() {
        deviceMapper = new DeviceMapper();
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
    }

    private GenericRecord newRecord(String deviceId, String deviceType, Long createdAt, String meta) {
        GenericRecord record = new GenericData.Record(deviceSchema);
        record.put("operation", "CREATE");
        record.put("deviceId", deviceId);
        record.put("deviceType", deviceType);
        record.put("createdAt", createdAt != null ? createdAt : 0L);
        record.put("meta", meta);
        return record;
    }

    @Test
    void whenValidRecordThenMapsToDevice() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);

        Device device = deviceMapper.toDevice(record);

        assertThat(device.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(device.deviceType()).isEqualTo(DEVICE_TYPE);
        assertThat(device.createdAt()).isEqualTo(CREATED_AT);
        assertThat(device.meta()).isEqualTo(META);
        assertThat(device.updatedAt()).isNotNull();
    }

    @Test
    void whenCreatedAtAsIntegerThenMapsAsLong() {
        GenericRecord record = new GenericData.Record(deviceSchema);
        record.put("operation", "CREATE");
        record.put("deviceId", DEVICE_ID);
        record.put("deviceType", DEVICE_TYPE);
        record.put("createdAt", 12345);
        record.put("meta", null);

        Device device = deviceMapper.toDevice(record);

        assertThat(device.createdAt()).isEqualTo(12345L);
    }

    @Test
    void whenNullRecordThenThrows() {
        assertThatThrownBy(() -> deviceMapper.toDevice(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GenericRecord cannot be null");
    }

    @Test
    void whenBlankDeviceIdThenThrows() {
        GenericRecord record = newRecord("", DEVICE_TYPE, CREATED_AT, META);

        assertThatThrownBy(() -> deviceMapper.toDevice(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }

    @Test
    void whenNullDeviceIdThenThrows() {
        GenericRecord record = newRecord(null, DEVICE_TYPE, CREATED_AT, META);

        assertThatThrownBy(() -> deviceMapper.toDevice(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }

    @Test
    void whenBlankDeviceTypeThenThrows() {
        GenericRecord record = newRecord(DEVICE_ID, "  ", CREATED_AT, META);

        assertThatThrownBy(() -> deviceMapper.toDevice(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceType");
    }

    @Test
    void whenNullCreatedAtThenThrows() {
        GenericRecord record = new GenericData.Record(deviceSchema);
        record.put("operation", "CREATE");
        record.put("deviceId", DEVICE_ID);
        record.put("deviceType", DEVICE_TYPE);
        record.put("createdAt", null);
        record.put("meta", META);

        assertThatThrownBy(() -> deviceMapper.toDevice(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void whenMetaNullThenMapsWithNullMeta() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, null);

        Device device = deviceMapper.toDevice(record);

        assertThat(device.meta()).isNull();
    }

    @Test
    void getOperationReturnsCreateWhenSet() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        assertThat(deviceMapper.getOperation(record)).isEqualTo("CREATE");
    }

    @Test
    void getOperationReturnsUpdateWhenSet() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        record.put("operation", "UPDATE");
        assertThat(deviceMapper.getOperation(record)).isEqualTo("UPDATE");
    }

    @Test
    void getOperationReturnsDeleteWhenSet() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        record.put("operation", "DELETE");
        assertThat(deviceMapper.getOperation(record)).isEqualTo("DELETE");
    }

    @Test
    void getOperationReturnsCreateWhenNullOrBlank() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        record.put("operation", null);
        assertThat(deviceMapper.getOperation(record)).isEqualTo("CREATE");
        record.put("operation", "  ");
        assertThat(deviceMapper.getOperation(record)).isEqualTo("CREATE");
    }

    @Test
    void getDeviceIdReturnsDeviceId() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        assertThat(deviceMapper.getDeviceId(record)).isEqualTo(DEVICE_ID);
    }

    @Test
    void toDeviceForUpdateMapsFields() {
        GenericRecord record = newRecord(DEVICE_ID, DEVICE_TYPE, CREATED_AT, META);
        record.put("operation", "UPDATE");
        DeviceUpdate update = deviceMapper.toDeviceForUpdate(record);
        assertThat(update.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(update.deviceType()).isEqualTo(DEVICE_TYPE);
        assertThat(update.meta()).isEqualTo(META);
    }

    @Test
    void toDeviceForUpdateUsesEmptyStringForNullOptionalFields() {
        GenericRecord record = new GenericData.Record(deviceSchema);
        record.put("operation", "UPDATE");
        record.put("deviceId", DEVICE_ID);
        record.put("deviceType", null);
        record.put("createdAt", null);
        record.put("meta", null);
        DeviceUpdate update = deviceMapper.toDeviceForUpdate(record);
        assertThat(update.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(update.deviceType()).isEmpty();
        assertThat(update.meta()).isEmpty();
    }
}
