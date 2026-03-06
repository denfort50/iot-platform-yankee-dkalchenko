## simple-event-sender

### Технологический стек

- **Язык**: Java 24
- **Фреймворк**: Spring Boot 3.5 (WebFlux, Validation, Actuator)
- **Интеграция с Kafka**: Reactor Kafka, Spring for Apache Kafka
- **Сериализация**: Apache Avro, Confluent Schema Registry
- **Avro-схемы**: зависимость `iot-avro-schemas` (DeviceEvent)

### Описание

`simple-event-sender` — это вспомогательный сервис платформы, который по HTTP‑запросу генерирует заданное количество 
событий `DeviceEvent` и асинхронно отправляет их в Kafka.  
Сервис использует Avro‑схему `DeviceEvent` и Confluent Schema Registry, работает на **Spring Boot 3.5 (WebFlux)** 
и **Reactor Kafka**.

**Основные возможности:**

- **HTTP API для генерации событий** в Kafka:
  - `POST /v1/events/generate` — генерация рандомных событий;
  - `POST /v1/events/generate-with-duplicates` — генерация событий с дубликатами `deviceId` (каждое 5‑е сообщение).
- **Реактивная отправка в Kafka** (`reactor-kafka`, `KafkaSender`).
- **Avro и Schema Registry**:
  - сериализация через `KafkaAvroSerializer`;
  - схема DeviceEvent загружается из зависимости `iot-avro-schemas` (`classpath:avro/DeviceEvent.avsc`).

---

## Конфигурация

### Kafka и Schema Registry

Базовая конфигурация (см. `application.yaml` и `application-*.yaml`):

- **Bootstrap servers**:
  - по умолчанию (docker): `kafka1:9092,kafka2:9093,kafka3:9094`
  - локально по умолчанию: `localhost:19092,localhost:19093,localhost:19094`
- **Schema Registry URL**:
  - по умолчанию (docker): `http://schema-registry:8081`
  - локально по умолчанию: `http://localhost:8081`
- **Топик**:
  - `app.kafka.topic.events` (по умолчанию `events.v1`)

**Переменные окружения:**

- **`KAFKA_BOOTSTRAP_SERVERS`** — адреса брокеров Kafka (опционально, есть дефолты в `application-*.yaml`).
- **`SCHEMA_REGISTRY_URL`** — URL Schema Registry (опционально, есть дефолты).

### Профили Spring

- **`local`**:
  - используется при `./gradlew bootRun` (см. `tasks.bootRun` в `build.gradle.kts`);
  - порт API: `8092`.
- **`docker`**:
  - используется в контейнере (см. `Dockerfile`: `SPRING_PROFILES_ACTIVE=docker`);
  - порт API: `8080`.

---

## HTTP API

Базовый путь: **`/v1/events`**.

### `POST /v1/events/generate`

- **Назначение**: сгенерировать указанное количество рандомных событий и отправить их в Kafka.
- **Тело запроса** (`application/json`):

```json
{
  "eventsAmt": 1000
}
```

- **Ограничения (`GenerateRequest`)**:
  - `eventsAmt` — **обязателен**;
  - `1 ≤ eventsAmt ≤ 100000000`.

- **Ответ (пример):**

```json
{
  "result": "1000 messages will be created. Check logs"
}
```

Сервис запускает генерацию **в фоне** и возвращает ответ сразу, не дожидаясь фактической отправки всех сообщений.

### `POST /v1/events/generate-with-duplicates`

- **Назначение**: сгенерировать события так, чтобы каждое 5‑е (5‑е, 10‑е, 15‑е, …) содержало `deviceId`, который уже встречался ранее.
- **Тело запроса** — такое же, как выше.
- **Ответ (пример):**

```json
{
  "result": "1000 messages (with duplicate deviceIds every 5th) will be created. Check logs"
}
```
---

## Локальный запуск

### Требования

- Java 24
- Gradle
- Docker
- Доступный кластер Kafka и Schema Registry

### Сборка

Из корня репозитория:

```bash
./gradlew :simple-event-sender:clean build
```

Из каталога `simple-event-sender`:

```bash
./gradlew clean build
```

На Windows (PowerShell): `.\gradlew clean build` (из каталога модуля).

### Запуск через Gradle (профиль `local`)

Из каталога `simple-event-sender`: `./gradlew bootRun`, или из корня репозитория: `./gradlew :simple-event-sender:bootRun`.

```bash
./gradlew bootRun
```

- Профиль: `local` (задаётся в `build.gradle.kts`).
- HTTP API: `http://localhost:8092`.
- По умолчанию:
  - `KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094`
  - `SCHEMA_REGISTRY_URL=http://localhost:8081`

При необходимости переопределите переменные окружения:

```bash

export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
export SCHEMA_REGISTRY_URL="http://localhost:8081"
./gradlew bootRun
```
---

## Запуск в Docker

Описан в [README.md](../README.md) верхнего уровня: `make all` или `make module-four` (корневая директория системы).

---

## Примеры запросов

### Генерация N событий

```bash
curl -X POST "http://localhost:8092/v1/events/generate" \
  -H "Content-Type: application/json" \
  -d '{"eventsAmt": 1000}'
```

### Генерация N событий с дубликатами

```bash
curl -X POST "http://localhost:8092/v1/events/generate-with-duplicates" \
  -H "Content-Type: application/json" \
  -d '{"eventsAmt": 1000}'
```
---
