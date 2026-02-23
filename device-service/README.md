# device-service

REST API для управления устройствами IoT-платформы. Контракт API и Feign-клиент — в модуле **device-service-api**.

## Функциональность

- **POST /devices** — создание устройства (публикация в Kafka `devices.v1`)
- **GET /devices/{deviceId}** — получение устройства из шардированной PostgreSQL с cache-aside через Redis
- **PUT /devices/{deviceId}** — обновление устройства (публикация в Kafka `devices.v1`)
- **DELETE /devices/{deviceId}** — удаление устройства (публикация в Kafka `devices.v1`)
- События аудита операций публикуются в топик **audit.device-events.v1** (Avro, Schema Registry).

## Зависимости

- **PostgreSQL** — шардированная БД (postgres-shard0/1/2), та же схема, что у device-collector-service
- **Kafka** — топик `devices.v1` для CREATE/UPDATE/DELETE; топик `audit.device-events.v1` для аудита
- **Redis** — cache-aside для GET
- **Keycloak** — OIDC/JWT аутентификация

## Безопасность

Все эндпоинты защищены OAuth2 Resource Server (JWT от Keycloak). Actuator endpoints (`/actuator/*`) доступны без аутентификации для мониторинга.

### Настройка Keycloak

1. Создайте realm (или используйте `master`)
2. Создайте OAuth2 client (confidential) с разрешёнными redirect URI
3. Используйте `KEYCLOAK_ISSUER_URI`: `http://keycloak:8080/realms/<realm>`
4. Для вызова API передавайте JWT в заголовке `Authorization: Bearer <token>`

## Метрики и логирование

- **Mетрики**: `/actuator/metrics`, `/actuator/prometheus`
- **Health**: `/actuator/health`
- CRUD метрики: `device.management.operations.total`, `device.management.operations.duration`, `device.management.db.errors.total`
- Логи в JSON для Loki

## Сборка образа

JAR собирается на хосте (зависимости тянутся с Maven/Confluent/Nexus без Docker), затем образ собирается из готового JAR:

```bash
# Из корня репозитория
make device-service-image
```

Или вручную:

```bash
./gradlew :device-service:clean :device-service:bootJar
docker build -f device-service/Dockerfile -t device-service:1.0.0 .
```

Для `docker compose up` JAR должен быть собран заранее (`./gradlew :device-service:clean :device-service:bootJar`). Полный запуск инфраструктуры и сервисов — см. [корневой README](../README.md) (`make all` или `make module-four`).

## Запуск

```bash
# Локально (profile: local)
./gradlew :device-service:bootRun

# Docker (образ должен быть собран, см. выше)
docker compose -f infrastructure/docker-compose.yaml up -d device-service
```

**Порты:** локально — приложение 8097, Actuator 9077; в Docker — реплики 8097–8099 (и 9077–9079 для Actuator), доступ через балансировщик **device-service-lb**: API 8090, Actuator 9070.
