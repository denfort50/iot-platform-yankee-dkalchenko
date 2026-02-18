## device-collector-service

### Технологический стек

- **Язык**: Java 24
- **Фреймворк**: Spring Boot 3.5 (Web, JDBC, Actuator)
- **Kafka**: Spring for Apache Kafka, Confluent Avro Serializer, Schema Registry
- **Хранение**: PostgreSQL, Flyway (в т.ч. MultiShardFlywayMigrator для шардов)
- **Шардирование**: Apache ShardingSphere-JDBC
- **Наблюдаемость**: Micrometer (Prometheus, OTLP), Spring Boot Actuator, OpenTelemetry (трассировка в Kafka)
- **Маппинг**: Lombok
- **Avro-схемы**: зависимость `iot-avro-schemas` (DeviceInfo, DeadLetterMessage)

### Описание

`device-collector-service` — это сервис для обработки сообщений об устройствах из Kafka и сохранения их в PostgreSQL с поддержкой шардирования через Apache ShardingSphere.

Сервис реализует:
- **Потребление сообщений** из Kafka топика `devices.v1` в формате Avro с валидацией через Schema Registry
- **Идемпотентность** через `ON CONFLICT (device_id) DO UPDATE` в PostgreSQL
- **Шардирование** через Apache ShardingSphere-JDBC по полю `device_id`
- **Обработку ошибок** с отправкой проблемных сообщений в Dead Letter Topic (`dlt.v1` по умолчанию)
- **Метрики и health checks** через Spring Actuator

**Поток данных:**

1. Сообщения об устройствах (схема Avro **DeviceInfo**) приходят в Kafka‑топик `devices.v1` (Schema Registry).
2. Листенер читает сообщения (`GenericRecord`), валидирует через Schema Registry и маппит в модель `Device`.
3. Устройство сохраняется или обновляется в PostgreSQL через `ON CONFLICT (device_id) DO UPDATE`.
4. ShardingSphere маршрутизирует запросы в нужный шард (таблица `collector.devices` в каждом шарде) по хешу `device_id`.
5. При ошибках обработки сообщение отправляется в Dead Letter Topic `dlt.v1` (формат DeadLetterMessage).

### Основные компоненты

- **Kafka Consumer**: `DeviceListener`
  - Читает `GenericRecord` из Kafka (`devices.v1`), валидирует через Schema Registry и передаёт в `DeviceService`.
  - Обрабатывает ошибки и отправляет проблемные сообщения в DLT.
- **Обработка устройств**: `DeviceService`
  - Сохраняет или обновляет устройство в PostgreSQL через `DeviceRepository`.
  - Использует `ON CONFLICT (device_id) DO UPDATE` для идемпотентности.
- **Хранение**: PostgreSQL + Flyway + ShardingSphere
  - Миграции в `db/migration`; в профиле `local`/`docker` применяются через **MultiShardFlywayMigrator** по каждому шарду.
  - В каждом шарде — таблица `collector.devices`; маршрутизация по хешу `device_id` (алгоритм `DeviceIdShardingAlgorithm`).
- **Конфигурация Kafka и Schema Registry**: `KafkaConfig`
  - Avro десериализация с Schema Registry, отправка в DLT при ошибках.
  - Проброс контекста трассировки: `KafkaTraceContextRunner` парсит trace_id из заголовков сообщения и создаёт спан с тем же trace_id.
- **Конфигурация ShardingSphere**: `sharding-sphere-config.yaml`
  - Определяет правила шардирования таблицы `devices` по полю `device_id`.
- **Метрики**: `CollectorMetrics` (Micrometer)
  - `device.collector.processed.total` — успешно обработанные сообщения (тег `shard`: 0, 1, 2, …).
  - `device.collector.failed.total` — ошибки обработки (по шарду).
  - `device.collector.dlt.sent.total` — сообщения, отправленные в DLT (по шарду).

---

## Конфигурация

Основные настройки задаются в `application.yaml` и профилях `application-local.yaml` / `application-docker.yaml`.

### Kafka

- Топик входа: `device-collector.listener.kafka-topic` (по умолчанию `devices.v1`).
- Dead Letter Topic: `device-collector.publisher.dlt-topic` (по умолчанию `dlt.v1`); дополнительно: `dlt-topic-partitions`, `dlt-topic-replicas`.
- Группа потребителя: `spring.kafka.consumer.group-id=device-collector-service-group`.
- Значения — Avro (`KafkaAvroDeserializer`), `schema.registry.url` задаётся переменной окружения `SCHEMA_REGISTRY_URL`.

**Переменные окружения (основные):**

- `KAFKA_BOOTSTRAP_SERVERS` — адреса брокеров Kafka.
- `SCHEMA_REGISTRY_URL` — URL Schema Registry.

### PostgreSQL и ShardingSphere

Подключение к БД идёт только через ShardingSphere: в `spring.datasource` указан `url: jdbc:shardingsphere:classpath:sharding-sphere-config.yaml`. **Каждый шард — отдельный сервер PostgreSQL** (или отдельная БД/порт). У каждого шарда — primary и read-реплика (read-write splitting).

- Файл конфигурации: `sharding-sphere-config.yaml`
- Три шарда: `ds0`, `ds1`, `ds2`; в каждом — таблица `collector.devices`. Маршрутизация по `device_id` через `DeviceIdShardingAlgorithm` (CLASS_BASED), read-write splitting по репликам.

**Переменные окружения** (подставляются в `sharding-sphere-config.yaml`):

Для каждого шарда задаются URL и при необходимости учётные данные (если не заданы, используются общие `POSTGRESQL_USER`, `POSTGRESQL_PASSWORD`):

- Шард 0: `POSTGRESQL_DS0_URL`, `POSTGRESQL_DS0_USER`, `POSTGRESQL_DS0_PASSWORD`; реплика: `POSTGRESQL_DS0_REPLICA_URL`, `POSTGRESQL_DS0_REPLICA_USER`, `POSTGRESQL_DS0_REPLICA_PASSWORD`
- Шард 1: `POSTGRESQL_DS1_URL`, `POSTGRESQL_DS1_USER`, `POSTGRESQL_DS1_PASSWORD`; реплика: `POSTGRESQL_DS1_REPLICA_URL`, …
- Шард 2: `POSTGRESQL_DS2_URL`, `POSTGRESQL_DS2_USER`, `POSTGRESQL_DS2_PASSWORD`; реплика: `POSTGRESQL_DS2_REPLICA_URL`, …

По умолчанию для локальной разработки (см. `src/main/resources/local.env`): три инстанса на localhost с портами **5436**, **5437**, **5438** и БД `iot_platform_shard0`, `iot_platform_shard1`, `iot_platform_shard2`. Реплики указывают на тот же URL, что и primary (одна нода на шард).

Схема и таблицы создаются миграциями в `db/migration`. В профилях `local` и `docker` стандартный Flyway отключён; миграции на все шарды выполняет **MultiShardFlywayMigrator** (прямое JDBC к каждому шарду). На каждом шарде создаётся схема `collector` и таблица `devices`.

### Профили Spring

- **`local`**:
  - используется при запуске через `./gradlew bootRun` (см. `tasks.bootRun` в `build.gradle.kts`);
  - задаёт локальные дефолты для Kafka и PostgreSQL (localhost, тестовые логин/пароль).
- **`docker`**:
  - используется в контейнере (см. `Dockerfile`: `SPRING_PROFILES_ACTIVE=docker`);
  - предполагает использование инфраструктуры из `docker-compose` (Kafka, PostgreSQL, Schema Registry).

---

## Локальный запуск

### Требования

- Java 24
- Gradle
- Docker (для Kafka, Schema Registry и трёх инстансов PostgreSQL под шарды)
- Доступные:
  - кластер Kafka и Schema Registry;
  - три инстанса PostgreSQL (шарды) с возможностью применения миграций через MultiShardFlywayMigrator.

### Сборка

Из корня репозитория:

```bash
./gradlew :device-collector-service:clean build
```

Из каталога `device-collector-service`:

```bash
./gradlew clean build
```

На Windows (PowerShell): `.\gradlew clean build` (из каталога модуля).

### Запуск через Gradle (профиль `local`)

Из каталога `device-collector-service`:

```bash
./gradlew bootRun
```

Или из корня репозитория: `./gradlew :device-collector-service:bootRun`.

- Профиль: `local` (задаётся в `build.gradle.kts` для `bootRun`).
- Миграции применяет **MultiShardFlywayMigrator** при старте (нужны три шарда PostgreSQL).
- Локальные значения по умолчанию (можно задать через `local.env` или переменные окружения):
  - `KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094`
  - `SCHEMA_REGISTRY_URL=http://localhost:8081`
  - Шарды: `POSTGRESQL_DS0_URL=jdbc:postgresql://localhost:5436/iot_platform_shard0?...`, порты 5436, 5437, 5438; пользователь/пароль — `admin`/`admin` (см. `src/main/resources/local.env`).

Пример переопределения переменных:

```bash
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092,localhost:9093,localhost:9094"
export SCHEMA_REGISTRY_URL="http://localhost:8081"
# для шардов используйте POSTGRESQL_DS0_URL, POSTGRESQL_DS1_URL, POSTGRESQL_DS2_URL и т.д.
./gradlew bootRun
```

---

## Запуск в Docker

Описан в [README.md](../README.md) верхнего уровня (в корневой директории всей системы).

---

## Структура базы данных

### Схема `collector`

В каждом шарде (отдельная БД) — одна и та же таблица `collector.devices`:

- `device_id` (VARCHAR(255), PRIMARY KEY) — уникальный идентификатор устройства
- `device_type` (VARCHAR(255)) — тип устройства
- `created_at` (BIGINT) — время создания (Unix timestamp, мс)
- `meta` (TEXT) — метаданные в формате JSON
- `updated_at` (TIMESTAMP) — время последнего обновления

ShardingSphere маршрутизирует записи по шардам по хешу `device_id` (алгоритм `DeviceIdShardingAlgorithm`). Дополнительно создаётся view `public.devices` для совместимости с ShardingSphere 5.x.

---

## Обработка ошибок

Сервис использует Dead Letter Topic для обработки ошибок:

1. При ошибке валидации данных (некорректный формат сообщения) сообщение отправляется в DLT.
2. При ошибке сохранения в БД сообщение отправляется в DLT после 3 попыток повтора.
3. Ошибки десериализации Avro обрабатываются через `ErrorHandlingDeserializer` и также отправляются в DLT.

Все ошибки логируются с указанием ключа, партиции и offset сообщения.
