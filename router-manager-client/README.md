# Router Manager Client

Отдельная Java-зависимость с gRPC-клиентом для `router-management-service`.

## Что входит в артефакт

- Сгенерированные protobuf message-классы.
- gRPC stubs (`RouterManagementServiceGrpc`) для sync/async вызовов.

## Источник контракта

- Единый модуль proto: **`router-management-api`** (`api/v1/router_management.proto`).

- Этот же модуль публикуется в Nexus как JAR `router-management-api` (дерево `proto/...` внутри артефакта).

- Во время сборки `router-manager-client` proto извлекается напрямую из Nexus-зависимости `ru.dkalchenko:router-management-api:<version>`.

- Версию контракта можно переопределить через `-ProuterManagementApiVersion=...` или ENV `ROUTER_MANAGEMENT_API_VERSION`.

## Подключение из Nexus

```kotlin

repositories {
    maven {
        url = uri("http://localhost:8082/repository/maven-releases/")
        isAllowInsecureProtocol = true
        credentials { ... }
    }
}

dependencies {
    implementation("ru.dkalchenko:router-manager-client:1.0.0")
}
```

## Публикация в Nexus

```bash

./gradlew :router-manager-client:publish

```
