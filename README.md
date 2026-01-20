# IoT Microservice Platform – проект, демонстрирующий архитектуру микросервисов для IoT (Internet of Things)

**Стек**: Java 24, Go 1.20+, Spring Boot 3.5, Web MVC, WebFlux, Security (JWT, OAuth2 Resource Server), OpenFeign, MapStruct, 
Lombok, Springdoc OpenAPI, Actuator + Micrometer Prometheus, OpenTelemetry (OTLP), Testcontainers, Docker Compose, 
Grafana + Alloy + Prometheus + Tempo + Loki, Keycloak, Camunda, ClickHouse, PostgreSQL, MinIO, Redis, Kafka.
---

## Содержание
* Архитектура
* Состав репозитория
* Быстрый старт
* Порты и сервисы
* Наблюдаемость

---
## Архитектура
### Диаграммы архитектуры (C4 + PlantUML)

Файлы находятся в каталоге `diagrams/`:
- **Context (уровень системы)** - [diagrams/context.puml](diagrams/context.puml)
- **Container (уровень системы)** - [diagrams/containers.puml](diagrams/containers.puml)

Рендерить можно любым PlantUML‑совместимым плагином/CI, файлы используют библиотеку **C4‑PlantUML** (включение по URL).

---
## Состав репозитория
```
iot-platform-yankee-dkalchenko/
├─ diagrams/
│  ├─ diagrams/containers.puml
│  ├─ diagrams/context.puml
├─ infrastructure/
│  ├─ alloy/config.alloy
│  ├─ databases/iot_platform/001_create_iot_platform.sql
│  ├─ grafana/provisioning/{datasources,dashboards}
│  ├─ loki/loki-config.yaml
│  ├─ prometheus/prometheus.yaml
│  ├─ tempo/tempo.yaml
│  ├─ .env
│  └─ docker-compose.yaml
├─ Makefile
└─ README.md
```

---
## Быстрый старт
> Требуется: **Docker** (compose), **JDK 24**, `make`. 
## Вариант A - через `Makefile` (рекомендуется)

```bash
# В корне репозитория
make start
```

Что делает:
* поднимает инфраструктуру  
(Keycloak + Postgres, Camunda + Postgres, Postgres для IoT, Prometheus, Grafana, Alloy, 
Tempo, Loki, экспортеры, Clickhouse, Redis, Redis UI, Zookeeper, Kafka, Schema Registry, Kafka UI, MinIO);
* стартует сервисы.

```bash
# Остановить контейнеры
make stop
```

```bash
# Удалить контейнеры и docker-тома
make clean
```

## Вариант B - чистый docker compose

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

| Сервис                           | Порт (host) | Описание                           |
|----------------------------------|-------------|------------------------------------|
| Postgres (iot-platform)          | 5434        | Контейнер `iot-platform-postgres`  |
| Postgres (keycloak)              | 5433        | Контейнер `keycloak-postgres`      |
| Postgres (camunda)               | 5435        | Контейнер `camunda-postgres`       |
| Clickhouse                       | 8123        | Хранилище телеметрии               |
| Redis                            | 6379        | Кэш и in-memory хранилище          |
| Redis Insight                    | 5540        | Визуальный интерфейс Redis         |
| Keycloak                         | 8080        | Dev-мод                            |
| Zookeeper                        | 2181        | Координация Kafka-кластера         |
| Kafka                            | 9092        | Брокер №1                          |
| Kafka                            | 9093        | Брокер №2                          |
| Kafka                            | 9094        | Брокер №3                          |
| Schema Registry                  | 8081        | Управление Avro схемами            |
| Kafka UI                         | 8070        | Визуальный интерфейс Kafka         |
| MinIO                            | 9001        | S3-совместимое объектное хранилище |
| Camunda                          | 8088        | Workflow и BPM движок              |
| Postgres Exporter (iot-platform) | 9188        | Экспорт метрик                     |
| Postgres Exporter (keycloak)     | 9189        | Экспорт метрик                     |
| Postgres Exporter (camunda)      | 9190        | Экспорт метрик                     |
| Kafka Exporter                   | 9308        | Экспорт метрик                     |
| Prometheus                       | 9090        | Метрики                            |
| Alloy                            | 9080        | OTel-коллектор                     |
| Grafana                          | 3000        | Dashboard + Explore                |
| Tempo                            | 3200        | Хранилище трассировок (OTLP)       |
| Loki                             | 3100        | Хранилище логов                    |

Grafana **автопровиженит** датасорсы Prometheus/Loki/Tempo.
---

## Наблюдаемость

* **Prometheus**: `http://localhost:9090`
* **Grafana**: `http://localhost:3000` (grafana_user/grafana_password). Датасорсы Prometheus/Loki/Tempo провиженятся из `grafana/provisioning`.
* **Трассировки**: отправляются по OTLP HTTP в `Tempo` (порт 4318), смотрятся в Grafana -> Explore -> Tempo.
* **Логи**: Loki развёрнут.
---

## Автор
* [Denis Kalchenko](https://github.com/denfort50)
