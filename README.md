# IoT Microservice Platform – система обработки событий электронных IoT-устройств

Платформа включает:
* **events-collector-service** (сбор событий, Outbox, публикация в Kafka и сохранение в ClickHouse)
* **event-service** (read-only REST API для чтения событий устройств из ClickHouse)
* **device-collector-service** (приём сообщений об устройствах из Kafka в шардированный PostgreSQL)
* **device-service** (REST API управления устройствами: CRUD, шардированный PostgreSQL, Redis cache-aside, Kafka, Keycloak JWT)
* **failed-events-processor** (обработка сообщений из Dead Letter Topic: парсинг, структурированный JSON и сохранение в MinIO)
* **router-management-service** (Go: gRPC API регистрации роутеров, отправки команд, опроса и подтверждения; отдельная БД PostgreSQL; JWT через Keycloak; health/metrics и опционально OTLP)
* **simple-event-sender** (генератор тестовых событий)

Общие Avro-схемы и REST-контракты вынесены в:
- **iot-avro-schemas**
- **event-service-api**
- **device-service-api**

## Технологический стек

- **Языки**: Java 24, Go 1.25+ (**router-management-service**)
- **Backend‑фреймворки**: Spring Boot 3.5 (Web MVC, WebFlux, Security — JWT, OAuth2 Resource Server)
- **Интеграция и клиентские библиотеки**: OpenFeign
- **Маппинг и удобство разработки**: MapStruct, Lombok, Springdoc OpenAPI
- **Хранение данных**: PostgreSQL (в т.ч. шардированный через ShardingSphere для device-collector), ClickHouse, Redis, MinIO (S3‑совместимое объектное хранилище)
- **Сообщения и очереди**: Kafka, Confluent Schema Registry
- **Оркестрация и инфраструктура**: Docker Compose
- **Безопасность и оркестрация процессов**: Keycloak, Camunda
- **API (дополнительно)**: gRPC (**router-management-service**, protobuf `api.v1`)
- **Наблюдаемость**: Spring Boot Actuator, Micrometer Prometheus, OpenTelemetry (OTLP), Grafana, Alloy, Prometheus, Tempo, Loki; Prometheus `/metrics` у **router-management-service**
- **Тестирование и окружения**: Testcontainers
---

## Содержание
- [Архитектура](#архитектура)
- [Состав репозитория](#состав-репозитория)
- [Router Management Service](#router-management-service)
- [Быстрый старт](#быстрый-старт)
- [Порты и сервисы](#порты-и-сервисы)
- [Наблюдаемость](#наблюдаемость)

---
## Архитектура
### Диаграммы архитектуры (C4 + PlantUML)

Диаграммы описывают платформу на уровнях C4: контекст системы, контейнеры (микросервисы), компоненты внутри сервисов и диаграммы последовательностей. Файлы находятся в каталоге `diagrams/`:

| Уровень        | Файл                                                | Описание                                                                                   |
|----------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------|
| **Context**    | [context.puml](diagrams/context.puml)               | Система в контексте: инженер, IoT-устройства, Kafka, Keycloak, Grafana, Camunda            |
| **Container**  | [containers.puml](diagrams/containers.puml)         | Контейнеры: ECS, DCS, Device Service, Failed Events Processor, MinIO, Kafka, observability |
| **Components** | [components_ecs.puml](diagrams/components_ecs.puml) | Events Collector: Event Listener, Deduplication, Outbox Processor, ClickHouse              |
|                | [components_dcs.puml](diagrams/components_dcs.puml) | Device Collector: Device Listener, DLT Producer, ShardingSphere → PostgreSQL               |
|                | [components_ds.puml](diagrams/components_ds.puml)   | Device Service: Device Controller, Redis Client, Device Changes Publisher                  |
|                | [components_fep.puml](diagrams/components_fep.puml) | Failed Events Processor: DLQ Listener, DLQ Service, DLQ Repository → MinIO                 |
| **Sequence**   | [sequence_ecs.puml](diagrams/sequence_ecs.puml)     | Поток событий ECS: Kafka → дедупликация → ClickHouse → Outbox → Kafka                      |
|                | [sequence_dcs.puml](diagrams/sequence_dcs.puml)     | Поток DCS: Kafka → устройство → PostgreSQL (или DLT при ошибке)                            |
|                | [sequence_ds.puml](diagrams/sequence_ds.puml)       | Поток Device Service: CRUD → Redis/PostgreSQL → Kafka                                      |
|                | [sequence_fep.puml](diagrams/sequence_fep.puml)     | Поток FEP: DLQ → парсинг → сохранение в MinIO                                              |

Рендерить можно любым PlantUML‑совместимым плагином/CI, файлы используют библиотеку **C4‑PlantUML** (включение по URL).

---
## Состав репозитория
```
iot-platform-yankee-dkalchenko/
├─ diagrams/                          # C4‑диаграммы архитектуры (PlantUML)
│  ├─ context.puml                    # Контекст (уровень системы)
│  ├─ containers.puml                 # Контейнеры (микросервисы, Kafka, MinIO, observability)
│  ├─ components_ecs.puml             # Компоненты Events Collector Service
│  ├─ components_dcs.puml             # Компоненты Device Collector Service
│  ├─ components_ds.puml              # Компоненты Device Service
│  ├─ components_fep.puml             # Компоненты Failed Events Processor
│  ├─ sequence_ecs.puml               # Последовательности ECS
│  ├─ sequence_dcs.puml               # Последовательности DCS
│  ├─ sequence_ds.puml                # Последовательности Device Service
│  └─ sequence_fep.puml               # Последовательности Failed Events Processor (DLQ → MinIO)
├─ iot-avro-schemas/                  # Общие Avro‑схемы (DeviceInfo, DeviceEvent, DeadLetterMessage, DeviceAuditEvent)
│  ├─ src/main/resources/avro/
│  └─ README.md
├─ device-service-api/               # OpenAPI‑спецификация и Feign‑клиент для REST API Device Service
│  ├─ src/main/resources/openapi/device-api.yaml
│  └─ README.md
├─ event-service-api/                # OpenAPI‑спецификация REST API Event Service (events read API)
│  └─ src/main/resources/openapi/event-api.yaml
├─ device-service/                   # REST API управления устройствами (CRUD, Kafka, шарды, Redis, Keycloak)
│  ├─ src/main/java/ru/dkalchenko/device/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  ├─ application-docker.yaml
│  │  └─ sharding-sphere-config.yaml
│  ├─ Dockerfile
│  └─ README.md
├─ event-service/                    # Read-only REST API чтения событий из ClickHouse
│  ├─ src/main/java/ru/dkalchenko/event/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  └─ application-docker.yaml
│  ├─ Dockerfile
│  └─ README.md
├─ device-collector-service/          # Сервис приёма сообщений об устройствах из Kafka → PostgreSQL (шарды)
│  ├─ src/main/java/ru/dkalchenko/device/collector/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  ├─ application-docker.yaml
│  │  └─ db/migration/...            # Flyway‑миграции, ShardingSphere
│  ├─ sharding-sphere-config.yaml
│  ├─ Dockerfile
│  └─ README.md
├─ events-collector-service/          # Сервис сбора и обработки событий IoT (Outbox → Kafka, ClickHouse)
│  ├─ src/main/java/ru/dkalchenko/collector/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  ├─ application-docker.yaml
│  │  └─ db/changelog/...            # Liquibase‑миграции для ClickHouse
│  ├─ Dockerfile
│  └─ README.md
├─ simple-event-sender/               # Сервис генерации тестовых событий в Kafka
│  ├─ src/main/java/ru/dkalchenko/sender/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  └─ application-docker.yaml
│  ├─ Dockerfile
│  └─ README.md
├─ failed-events-processor/          # Обработка DLT (dlt.v1): парсинг DeadLetterMessage → JSON в MinIO
│  ├─ src/main/java/ru/dkalchenko/failedevents/...
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  ├─ application-local.yaml
│  │  └─ application-docker.yaml
│  ├─ Dockerfile
│  └─ README.md
├─ router-management-service/        # Go: gRPC управление роутерами (PostgreSQL, Keycloak JWT, scheduler)
│  ├─ api/proto/api/v1/              # router_management.proto и сгенерированный Go-код
│  ├─ cmd/server/
│  ├─ internal/                      # config, repository, service, grpc handler, auth, telemetry, metrics
│  ├─ integration/                   # интеграционные тесты (Testcontainers)
│  ├─ Dockerfile
│  └─ README.md
├─ load-testing/                     # JMeter: нагрузочные сценарии для router-management-service
│  ├─ router-management-load-test.jmx
│  ├─ run-jmeter.ps1
│  └─ run-jmeter.sh
├─ infrastructure/                    # Инфраструктура: observability, БД, nginx (LB)
│  ├─ alloy/config.alloy
│  ├─ grafana/provisioning/{datasources,dashboards}
│  ├─ grafana/dashboards/
│  ├─ nginx/nginx.conf                # Балансировщик для device-service (порт 8090)
│  ├─ docker-compose.yaml
│  ├─ .env
│  └─ ...
├─ build.gradle.kts                   # Корневой Gradle‑конфиг (мультипроект)
├─ settings.gradle.kts                # Настройка модулей Gradle
├─ Makefile                           # Сценарии: all, module-five, module-six, stop, clean, *-image
└─ README.md
```

---
## Router Management Service

Отдельный модуль на **Go** с **gRPC** API (`RouterManagementService`): регистрация роутера, отправка команд одному или всем зарегистрированным роутерам, опрос очереди команд и подтверждение выполнения. Данные — в выделенном **PostgreSQL** (`router-management-postgres` в Compose). Аутентификация — **JWT** по issuer Keycloak (как и у остальной платформы). В том же процессе поднимается HTTP (**Gin**): `/health`, `/ready`, `/metrics` (Prometheus); при настройке OTLP — трейсы в **Alloy/Tempo**.

- Подробная документация, переменные окружения, сборка и тесты: [`router-management-service/README.md`](router-management-service/README.md).

---
## Быстрый старт
> Требуется: **Docker** (compose), **JDK 24**, `make`.

### Вариант A — через `Makefile` (рекомендуется)

```bash
# Полный запуск: поднимает Kafka, Schema Registry, Nexus → собирает iot-avro-schemas, device-service-api и event-service-api → поднимает все контейнеры
make all
```

```bash
# Остановить контейнеры
make stop
```

```bash
# Удалить контейнеры и тома
make clean
```

### Вариант B — чистый docker compose

```bash
# Запустить контейнеры
docker compose -f infrastructure/docker-compose.yaml up -d
```

```bash
# Остановить контейнеры
docker compose -f infrastructure/docker-compose.yaml stop
```

```bash
# Удалить контейнеры и docker-тома
docker compose -f infrastructure/docker-compose.yaml down -v
```

---
## Порты и сервисы

| Сервис                               | Порт (host)      | Описание                                   |
|--------------------------------------|------------------|--------------------------------------------|
| Postgres Shard 0 (device-collector)  | 5436             | Контейнер `postgres-shard0`                |
| Postgres Shard 1 (device-collector)  | 5437             | Контейнер `postgres-shard1`                |
| Postgres Shard 2 (device-collector)  | 5438             | Контейнер `postgres-shard2`                |
| Postgres (keycloak)                  | 5433             | Контейнер `keycloak-postgres`              |
| Postgres (camunda)                   | 5435             | Контейнер `camunda-postgres`               |
| ClickHouse                           | 8123             | Хранилище телеметрии                       |
| Redis                                | 6379             | Кэш и in-memory хранилище                  |
| Redis Insight                        | 5540             | Визуальный интерфейс Redis                 |
| Keycloak                             | 8080             | Dev-мод                                    |
| Kafka                                | 9092, 9093, 9094 | Брокеры №1–3                               |
| Schema Registry                      | 8081             | Управление Avro-схемами                    |
| Kafka UI                             | 8070             | Визуальный интерфейс Kafka                 |
| Nexus                                | 8082             | Maven-репозиторий (iot-avro-schemas и др.) |
| MinIO                                | 9001, 9002       | S3-совместимое хранилище + консоль         |
| Camunda                              | 8088             | Workflow и BPM движок                      |
| Postgres Exporter (shard 0/1/2)      | 9188, 9191, 9192 | Метрики PostgreSQL шардов                  |
| Postgres Exporter (keycloak)         | 9189             | Метрики Keycloak Postgres                  |
| Postgres Exporter (camunda)          | 9190             | Метрики Camunda Postgres                   |
| Kafka Exporter                       | 9308             | Метрики Kafka                              |
| Prometheus                           | 9090             | Сбор метрик                                |
| Alloy                                | 9080             | OTel-коллектор (трассировки, метрики)      |
| Grafana                              | 3000             | Dashboard + Explore                        |
| Tempo                                | 3200             | Хранилище трассировок (OTLP)               |
| Loki                                 | 3100             | Хранилище логов                            |
| Device Service                       | 8097–8099        | REST API управления устройствами (реплики) |
| Device Service Load Balancer (Nginx) | 8090, 9070       | API device-service (балансировка)          |
| Device Collector Service             | 8096             | Приём устройств из Kafka → PostgreSQL      |
| Events Collector Service             | 8093–8095        | Сбор событий (Outbox → Kafka, ClickHouse)  |
| Event Service                        | 8100, 9081       | Read-only API событий из ClickHouse (API, actuator) |
| Simple Event Sender                  | 8092             | REST API генерации тестовых событий        |
| Failed Events Processor              | 8098, 9078       | Обработка DLT → JSON в MinIO (API, actuator) |
| Router Management Service (gRPC)    | 50051            | Контейнер `router-management-service`      |
| Router Management Service (HTTP)    | 8091             | Health / ready / metrics (проброс с 8080)    |
| Postgres (router-management)        | 5439             | Контейнер `router-management-postgres`     |
| Postgres Exporter (router-management) | 9193           | Метрики БД router-management               |

## Наблюдаемость

* **Prometheus**: `http://localhost:9090`
* **Grafana**: `http://localhost:3000` (grafana_user/grafana_password). Датасорсы Prometheus/Loki/Tempo провиженятся из `grafana/provisioning`.
* **Трассировки**: отправляются по OTLP HTTP в `Tempo` (порт 4318), смотрятся в Grafana -> Explore -> Tempo.
* **Логи**: Loki развёрнут.
---

## Автор
* [Denis Kalchenko](https://github.com/denfort50)
