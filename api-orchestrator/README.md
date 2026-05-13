# api-orchestrator

Фасадный **Spring WebFlux**‑сервис: единая HTTP‑точка входа для клиентов. Проксирует вызовы в **device-service** (OpenFeign), **event-service** (OpenFeign) и **router-management-service** (gRPC), реализует сценарий **саги обновления целевой версии ПО** устройства с компенсацией при ошибке роутера.

OpenAPI‑контракт и публикуемые артефакты API вынесены в модуль **api-orchestrator-api** (`orchestrator-api.yaml`). Для интеграционных запросов удобна коллекция [postman/api-orchestrator.postman_collection.json](../postman/api-orchestrator.postman_collection.json).

## Технологический стек

- **Язык**: Java 24
- **Фреймворк**: Spring Boot 3.5 (WebFlux, OAuth2 Resource Server, Validation, Actuator)
- **Интеграции**: Spring Cloud OpenFeign (+ LoadBalancer), gRPC (Netty), Resilience4j (circuit breaker, retry, time limiter)
- **Наблюдаемость**: Micrometer (Prometheus), OpenTelemetry (трассировка, OTLP в Docker), Feign/Micrometer
- **Контракт**: OpenAPI Generator (reactive, delegate pattern) по спецификации из `api-orchestrator-api`
- **Клиентские API‑модули**: `device-service-api`, `event-service-api`, `router-manager-client`
- **Тесты**: JUnit 5, Reactor Test, WireMock, Testcontainers, ArchUnit

## Функциональность (HTTP)

Пути соответствуют [orchestrator-api.yaml](../api-orchestrator-api/src/main/resources/openapi/orchestrator-api.yaml).

| Область | Методы и пути | Назначение |
|--------|----------------|------------|
| **Commands** | `POST /commands`, `POST /commands/poll`, `POST /commands/ack` | Проксирование gRPC router-management (отправка команды, опрос очереди, подтверждение) |
| **Routers** | `POST /routers/register` | Регистрация роутера, возврат `routerId` |
| **Devices** | `POST /devices`, `GET/PUT/DELETE /devices/{deviceId}`, `PUT /devices/{deviceId}/version` | CRUD через device-service; `PUT .../version` — оркестрируемое обновление целевой версии (device-service + команда прошивки роутеру, при сбое — откат целевой версии в device-service) |
| **Events** | `GET /events`, `GET /events/{eventId}` | Чтение событий через event-service (фильтры и пагинация как в upstream) |

Ответы об ошибках в формате **Problem Details** (`OrchestratorExceptionHandler`).

## Сага обновления версии

`VersionUpdateSagaService`: загрузка устройства → установка целевой версии в device-service → отправка команды обновления прошивки в router-management. При ошибке роутера выполняется **компенсация** (восстановление прежней целевой версии в device-service). Опциональный **`idempotencyKey`** в теле запроса кэшируется в памяти (`InMemoryVersionUpdateIdempotencyAdapter`) для повторных запросов с тем же ключом.

Метрики саги: `OrchestratorMetrics` — `orchestrator.version.update.total` (тег `outcome`: success / compensated / failure), `orchestrator.version.update.duration`, `orchestrator.version.update.idempotent.replay.total`.

## Основные компоненты

- **Входящий слой**: делегаты OpenAPI (`DeviceProxyController`, `EventProxyController`, `CommandProxyController`, `RouterProxyController`) → use case‑интерфейсы в `application.port.in`.
- **Прикладной слой**: `DeviceApplicationService`, `EventApplicationService`, `CommandApplicationService`, `VersionUpdateSagaService`.
- **Исходящие адаптеры**: `ResilientDeviceOutboundAdapter`, `ResilientEventOutboundAdapter` (Feign + Resilience4j), `ResilientRouterCommandAdapter` (gRPC + Resilience4j).
- **Feign**: `BearerForwardingInterceptor` — проброс JWT из входящего запроса в upstream REST.
- **Безопасность**: `SecurityConfiguration` — JWT (issuer из `KEYCLOAK_ISSUER_URI`); `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics/**` без аутентификации.

## Зависимости (runtime)

- **device-service** — REST (`clients.device-service.url`, в Docker через балансировщик `device-service-lb`)
- **event-service** — REST
- **router-management-service** — gRPC (`clients.router-management.host` / `port`)
- **Keycloak** — OIDC/JWT для бизнес‑эндпойнтов

## Конфигурация

Основные файлы: `application.yaml`, профили `application-local.yaml`, `application-docker.yaml`.

**Переменные окружения (типовые):**

| Переменная | Назначение |
|------------|------------|
| `KEYCLOAK_ISSUER_URI` | Issuer JWT (OAuth2 Resource Server) |
| `DEVICE_SERVICE_URL` | Базовый URL device-service (в `application.yaml` по умолчанию суффикс `/v1`) |
| `EVENT_SERVICE_URL` | Базовый URL event-service |
| `ROUTER_MANAGEMENT_GRPC_HOST`, `ROUTER_MANAGEMENT_GRPC_PORT` | Хост и порт gRPC router-management |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | OTLP endpoint для трассировки (в Docker — Alloy) |

**Resilience4j** (circuit breaker / retry / time limiter) настроен в `application.yaml` для инстансов `deviceService`, `eventService`, `routerManagement`.

### Профили Spring

- **`local`** — порты и URL для разработки на хосте (см. `application-local.yaml`).
- **`docker`** — значения из `docker-compose`, приложение на **8080**, Actuator на **9090** (`application-docker.yaml`).

## Метрики и логирование

- **Actuator**: `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`
- HTTP‑метрики Spring Boot для бизнес‑запросов: `http.server.requests`
- Дополнительные метрики саги — см. раздел «Сага обновления версии»
- Дашборд Grafana (пример): [infrastructure/grafana/dashboards/api-orchestrator.json](../infrastructure/grafana/dashboards/api-orchestrator.json)

## Сборка образа

JAR собирается на хосте, затем собирается Docker‑образ:

```bash
# Из корня репозитория
make api-orchestrator-image
```

Или вручную:

```bash
./gradlew :api-orchestrator:clean :api-orchestrator:bootJar
docker build -f api-orchestrator/Dockerfile -t api-orchestrator:1.0.0 .
```

Для `docker compose up` образ/JAR должны быть подготовлены заранее. Полный запуск стека — см. [корневой README](../README.md) (`make all` и т.п.).

## Запуск

```bash
# Локально (profile: local)
./gradlew :api-orchestrator:bootRun
```

```bash
# Docker (после сборки образа)
docker compose -f infrastructure/docker-compose.yaml up -d api-orchestrator
```

**Порты:** локально — приложение **8099**, Actuator **9079**; в Docker (маппинг из compose) — приложение **8101**→8080 внутри контейнера, Actuator **9082**→9090.

## Тесты

```bash
./gradlew :api-orchestrator:test
```

В проекте есть unit‑тесты адаптеров и саги, WebFlux‑тесты обработчика исключений, интеграционные сценарии на Testcontainers (`FullIntegrationTests`, базовый класс в `testcontainer`), проверка архитектурных правил (ArchUnit).

## Визуальные материалы

В каталоге [img/](img/) — скриншоты трассировки и дашборда для отчётов и презентаций.
