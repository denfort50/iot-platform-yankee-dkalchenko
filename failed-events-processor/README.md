# failed-events-processor

Сервис обработки сообщений из Kafka Dead Letter Topic (dlt.v1): десериализует DeadLetterMessage (Avro), парсит `originalValue` как DeviceEvent или DeviceInfo, формирует структурированный JSON и сохраняет файлы в MinIO (бакеты `failed-events` и `failed-devices`).

## Зависимости

- **Kafka** — подписка на топик `dlt.v1`
- **Schema Registry** — десериализация Avro (DeadLetterMessage)
- **MinIO** — объектное хранилище (S3-совместимое)
- **iot-avro-schemas** — схемы DeadLetterMessage, DeviceEvent, DeviceInfo

## Поведение

1. Читает сообщения из топика `dlt.v1` (значение — Avro DeadLetterMessage).
2. Из записи извлекает поля `originalValue` (JSON-строка), `service`, `errorMessage` и т.д.
3. Парсит `originalValue`:
   - при наличии поля `eventId` — считает сообщение **DeviceEvent** → формирует **FailedEventJson** и сохраняет в бакет **failed-events**;
   - при наличии поля `operation` — считает сообщение **DeviceInfo** → формирует **FailedDeviceJson** и сохраняет в бакет **failed-devices**.
4. Имя файла в MinIO: `{eventType}_{timestamp}_{uuid}.json` (например, `TEMPERATURE_1718893845777_a1b2c3d4-....json`).
5. Бакеты `failed-events` и `failed-devices` создаются при старте сервиса, если их ещё нет.

## Конфигурация

- `failed-events-processor.listener.dlt-topic` — топик DLT (по умолчанию `dlt.v1`).
- `failed-events-processor.minio.endpoint`, `access-key`, `secret-key`, имена бакетов — настройки MinIO.

Переменные окружения (в т.ч. для Docker): `KAFKA_BOOTSTRAP_SERVERS`, `SCHEMA_REGISTRY_URL`, `MINIO_ENDPOINT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`.

## Метрики и health

- **Actuator**: `/actuator/health` (включая кастомный индикатор MinIO), `/actuator/prometheus`.
- Метрики: `failed.events.processor.saved.events.total`, `failed.events.processor.saved.devices.total`, `failed.events.processor.dlt.invalid.total`, `failed.events.processor.dlt.unknown.total`, `failed.events.processor.errors.total`.

## Локальный запуск

1. Поднять Kafka, Schema Registry, MinIO (например, через `docker-compose` в `infrastructure/`).
2. `./gradlew :failed-events-processor:bootRun` (профиль `local`, порты 8098 / 9078).

## Сборка и Docker

```bash
./gradlew :failed-events-processor:clean :failed-events-processor:bootJar
docker build -f failed-events-processor/Dockerfile -t failed-events-processor:1.0.0 .
```

В `infrastructure/docker-compose.yaml` сервис `failed-events-processor` зависит от Kafka, Schema Registry и MinIO.
