# event-service

Read-only REST API для чтения событий устройств из ClickHouse. OpenAPI-контракт и модели вынесены в модуль **event-service-api**.

## Функциональность

- **GET /api/v1/events** — список событий устройства с фильтрами (`fromTimestamp`, `toTimestamp`, `type`) и пагинацией (`page`, `size`)
- **GET /api/v1/events/{eventId}** — получение конкретного события по `eventId` и `deviceId`
- Валидация входных параметров и ответы в формате Problem Details (`400/404/500`)

## Зависимости

- **ClickHouse** — чтение данных из таблицы `collector.device_events`
- **Keycloak** — OIDC/JWT аутентификация (OAuth2 Resource Server)

## Безопасность

Все бизнес-эндпойнты требуют JWT-токен. Actuator endpoints (`/actuator/*`) доступны без аутентификации для мониторинга.

### Настройка Keycloak

1. Создайте realm (или используйте `master`)
2. Создайте OAuth2 client (confidential)
3. Укажите `KEYCLOAK_ISSUER_URI`: `http://keycloak:8080/realms/<realm>`
4. Передавайте JWT в заголовке `Authorization: Bearer <token>`

## Метрики и логирование

- **Метрики**: `/actuator/metrics`, `/actuator/prometheus`
- **Health**: `/actuator/health`
- HTTP-метрики Spring Boot Actuator для бизнес-эндпойнтов: `http.server.requests` (в Prometheus: `http_server_requests_seconds_*`)

## Сборка образа

JAR собирается на хосте, затем образ собирается из готового JAR:

```bash
# Из корня репозитория
make event-service-image
```

Или вручную:

```bash
./gradlew :event-service:clean :event-service:bootJar
docker build -f event-service/Dockerfile -t event-service:1.0.0 .
```

Для `docker compose up` JAR должен быть собран заранее (`./gradlew :event-service:clean :event-service:bootJar`). Полный запуск инфраструктуры и сервисов — см. [корневой README](../README.md) (`make all` или `make module-six`).

## Запуск

```bash
# Локально (profile: local)
./gradlew :event-service:bootRun

# Docker (образ должен быть собран, см. выше)
docker compose -f infrastructure/docker-compose.yaml up -d event-service
```

**Порты:** локально — приложение `8098`, Actuator `9078`; в Docker — приложение `8100`, Actuator `9081`.
