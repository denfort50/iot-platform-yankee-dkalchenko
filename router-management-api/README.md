# Router Management API Contracts

Отдельный модуль с **одним источником истины** для gRPC-контракта Router Management.

## Содержимое

- `api/v1/router_management.proto` — описание сервиса и сообщений.
- `buf.yaml` — конфигурация Buf-модуля для локальной валидации/генерации.

## Gradle

Публикуется в Nexus как JAR с деревом `proto/api/v1/...` внутри артефакта (удобно для потребителей Maven).

```bash
./gradlew :router-management-api:publish
```

