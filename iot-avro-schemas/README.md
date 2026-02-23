# iot-avro-schemas

Библиотека общих Avro-схем для платформы IoT. Единственный источник правды для схем:
- **DeviceInfo** (топик `devices.v1`)
- **DeviceEvent** (топик `events.v1`)
- **DeadLetterMessage** (DLT)

Используется сервисами: `events-collector-service`, `device-collector-service`, `simple-event-sender`.

## Сборка

```bash
./gradlew :iot-avro-schemas:build
```

При выполнении `build` автоматически:
1. **Регистрируются схемы** в Schema Registry (`http://localhost:8081`);
2. **Публикуется JAR** в Nexus (`http://localhost:8082/repository/maven-releases/`).

Нужны запущенные контейнеры Schema Registry и Nexus (например, `make only-needed`). В JAR попадают ресурсы `avro/*.avsc`, сервисы загружают их с classpath (`ClassPathResource("avro/DeviceInfo.avsc")` и т.д.).

## Регистрация схем в Schema Registry

Задача `registerSchemas` вызывается автоматически при `build` (и при `publish`). Запустить вручную:

```bash
./gradlew :iot-avro-schemas:registerSchemas
```

По умолчанию используется `http://localhost:8081`. Можно задать URL через переменную окружения или свойство:

```bash
export SCHEMA_REGISTRY_URL=http://localhost:8081
./gradlew :iot-avro-schemas:registerSchemas
# или
./gradlew :iot-avro-schemas:registerSchemas -PschemaRegistry.url=http://localhost:8081
```

## Публикация в Nexus

Параметры по умолчанию соответствуют поднятому Nexus из `infrastructure/docker-compose.yaml` (порт **8082** на хосте):

| Переменная / свойство | По умолчанию |
|------------------------|--------------|
| `NEXUS_URL` / `nexus.url` | `http://localhost:8082/repository/maven-releases/` |
| `NEXUS_USER` / `nexus.user` | `admin` |
| `NEXUS_PASSWORD` / `nexus.password` | из `infrastructure/.env`: `admin` |

Публикация в Nexus (и регистрация схем) выполняется и при `build`, и при явном вызове:

```bash
./gradlew :iot-avro-schemas:publish
# или
./gradlew :iot-avro-schemas:buildAndPublish
```

Для публикации в другой Nexus или с другими учётными данными:

```bash
export NEXUS_URL=http://ci.example.com/repository/maven-releases/
export NEXUS_USER=deploy
export NEXUS_PASSWORD=secret
./gradlew :iot-avro-schemas:publish
```

## Подключение в сервисах

В монорепозитории сервисы подключают библиотеку как проектную зависимость:

```kotlin
implementation(project(":iot-avro-schemas"))
```

После публикации в Nexus можно подключать артефакт из репозитория (для CI или отдельных сборок):

```kotlin
repositories {
    maven {
        url = uri("http://localhost:8082/repository/maven-releases/")
        isAllowInsecureProtocol = true
    }
}
dependencies {
    implementation("ru.dkalchenko:iot-avro-schemas:1.0.1")
}
```
