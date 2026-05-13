# Router Management Service (Go)

gRPC API для управления роутерами: регистрация роутера, отправка команд (одному или всем зарегистрированным), опрос команд роутерами, подтверждение выполнения. Данные хранятся в PostgreSQL. HTTP в том же процессе: health/ready, Prometheus `/metrics`, опционально трейсы OpenTelemetry (OTLP/HTTP).

## Требования

- Go 1.25+ (см. `go.mod`)
- PostgreSQL 17 (или совместимая)
- Контракт gRPC публикуется модулем `router-management-api` в Nexus как JAR (единый источник истины).
- Для перегенерации Go: `protoc`, `protoc-gen-go`, `protoc-gen-go-grpc` (цель `make generate` сама скачает proto из Nexus).

## Конфигурация

При старте подгружается `.env` из текущей директории (если есть), затем YAML по `CONFIG_PATH` и переопределение ENV.

| ENV                                  | Описание                                                                                                  | По умолчанию              |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------|---------------------------|
| `CONFIG_PATH`                        | Путь к YAML-конфигу                                                                                       | —                         |
| `GRPC_PORT`                          | Порт gRPC                                                                                                 | 50051                     |
| `HTTP_PORT`                          | Порт HTTP (health/ready/metrics)                                                                          | 8080                      |
| `POSTGRES_HOST`                      | Хост PostgreSQL                                                                                           | localhost                 |
| `POSTGRES_PORT`                      | Порт PostgreSQL                                                                                           | 5432                      |
| `POSTGRES_USER`                      | Пользователь                                                                                              | postgres                  |
| `POSTGRES_PASSWORD`                  | Пароль                                                                                                    | —                         |
| `POSTGRES_DB`                        | Имя БД                                                                                                    | router_management         |
| `POSTGRES_SSLMODE`                   | Режим SSL (`disable`, `require`, …)                                                                       | disable                   |
| `SCHEDULER_ENABLED`                  | Планировщик зависших команд (`true`/`false`)                                                              | false                     |
| `SCHEDULER_CRON`                     | Cron (5 полей, без секунд)                                                                                | `*/1 * * * *`             |
| `SENT_TIMEOUT_MINUTES`               | Через сколько минут после `sent_at` помечать команду FAILED                                               | 10                        |
| `KEYCLOAK_ISSUER_URI`                | Issuer Keycloak (OIDC), например `http://host.docker.internal:8080/realms/master`                         | —                         |
| `AUTH_CLIENT_ID`                     | OAuth2 `client_id`, для которого выдан токен: в JWT проверяются `aud` и/или `azp` (Keycloak access token) | —                         |
| `AUTH_ENABLED`                       | Проверка JWT (`true`/`false`). При заданном `KEYCLOAK_ISSUER_URI` по умолчанию включается                 | —                         |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | Полный URL OTLP HTTP для трейсов, например `http://alloy:4318/v1/traces`                                  | —                         |
| `OTEL_SERVICE_NAME`                  | Имя сервиса в трейсах                                                                                     | router-management-service |

В YAML см. `config.example.yaml`: `server`, `postgres`, `scheduler`, `auth`, секция `telemetry` (опционально: `otlp_traces_endpoint`, `service_name`).

Если включена проверка JWT (`AUTH_ENABLED` или задан `KEYCLOAK_ISSUER_URI`), нужно указать **`AUTH_CLIENT_ID`** (или `auth.client_id` в YAML): это ожидаемая аудитория токена; иначе сервис не стартует.

## Сборка и запуск

```bash
make build
# или
go build -o router-management-service.exe ./cmd/server
```

Задать `POSTGRES_*` и при необходимости `CONFIG_PATH`, затем:

```bash
./router-management-service.exe
# или
make run
```

Локально с Docker Compose (из корня репозитория):

```bash
cd infrastructure
docker compose up -d router-management-postgres router-management-service
```

- gRPC: порт **50051** (host).
- HTTP: внутри контейнера **8080**, снаружи проброшен на **8091** — health: `http://localhost:8091/health`, readiness: `http://localhost:8091/ready`, метрики Prometheus: `http://localhost:8091/metrics`.

## API (gRPC, namespace `api.v1`)

- **RegisterRouter** — регистрация роутера (инвентарный номер, имя, опционально модель и локация); в ответе — сгенерированный `router_id` (UUID).
- **SendCommand** — отправка команды: при непустом `router_id` создаётся одна команда; при **пустом** `router_id` — по одной команде на каждого зарегистрированного роутера. В ответе — список `command_ids`. Тип: `REBOOT`, `PING` и т.д., опционально `payload` (JSON).
- **CommandsPoll** — роутер по `router_id` запрашивает команды (PENDING/SENT); при выдаче статус переводится в SENT, обновляется `last_seen_at`.
- **CommandsAck** — подтверждение выполнения (`router_id`, `command_id`); статус → ACKED.

## Тесты

```bash
make test
# или
go test ./...
```

Используется mock-хранилище в `internal/repository/mock.go` для unit-тестов сервиса.

### Интеграционные тесты (Testcontainers)

В `integration/` — полный сценарий: PostgreSQL в Docker, миграции, при необходимости Keycloak, gRPC-сервер и проверка `RegisterRouter` → `SendCommand` (одному) → `CommandsPoll` → `CommandsAck` → `SendCommand` (всем) и повторный опрос. Нужен запущенный Docker.

```bash
make test-integration
# или
go test -tags=integration ./integration/ -v -count=1
```

### Smoke-тест gRPC

Скрипты `scripts/smoke-test.sh` (Linux/macOS) и `scripts/smoke-test.ps1` (Windows): полный цикл через `grpcurl`; при включённом JWT — переменная `JWT_TOKEN`. Запуск: `make smoke` (на Windows — см. `scripts/smoke-test.ps1`).

## Регенерация proto

`make generate` скачивает `router-management-api` из Nexus, извлекает `proto/api/v1/router_management.proto` в локальный кэш `.proto-cache/` и регенерирует Go-код.

Базовый запуск:

```bash
make generate
```

При необходимости можно переопределить источник/версию:

```bash
make generate NEXUS_URL=http://localhost:8082/repository/maven-releases ROUTER_API_VERSION=1.0.0 NEXUS_USER=admin NEXUS_PASSWORD=admin
```

Сгенерированные Go-файлы попадают в `api/proto/api/v1/` этого сервиса (см. `go_package` в proto).

Очистка локального кэша скачанного артефакта:

```bash
make clean-proto-cache
```
