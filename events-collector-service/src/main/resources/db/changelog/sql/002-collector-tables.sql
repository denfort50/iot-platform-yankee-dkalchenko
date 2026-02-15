CREATE TABLE IF NOT EXISTS collector.device_events (
    device_id     String,
    event_id      String,
    event_date    Date,
    timestamp_ms  Int64,
    type          String,
    payload       String
)
ENGINE = MergeTree
PARTITION BY event_date
ORDER BY (device_id, event_date, timestamp_ms, event_id);

CREATE TABLE IF NOT EXISTS collector.device_outbox (
    device_id     String,
    device_type   String,
    created_at    Int64,          -- epoch millis, соответствует полю createdAt в Avro-схеме DeviceInfo
    meta          String,
    attempts      UInt32,
    last_error    String,
    -- Контекст трейса для сквозного трейсинга (events.v1 -> devices.v1)
    trace_id      Nullable(String),
    span_id       Nullable(String)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(toDateTime(created_at / 1000))
ORDER BY (created_at, device_id);
