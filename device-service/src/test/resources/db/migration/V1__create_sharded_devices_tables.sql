CREATE SCHEMA IF NOT EXISTS collector;

CREATE TABLE IF NOT EXISTS collector.devices (
    device_id VARCHAR(255) NOT NULL PRIMARY KEY,
    device_type VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    meta TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_devices_device_id ON collector.devices(device_id);
COMMENT ON TABLE collector.devices IS 'Устройства (шардируется по device_id на уровне БД)';

-- View в public — обход TableNotFoundException в ShardingSphere 5.x
CREATE OR REPLACE VIEW public.devices AS SELECT * FROM collector.devices;
