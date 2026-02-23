# Device Service API

Отдельная зависимость: **OpenAPI 3 спецификация** и **Feign-клиент** для REST API Device Service.

## Содержимое

- **OpenAPI spec:** `src/main/resources/openapi/device-api.yaml` (входит в JAR как ресурс).
- **Сгенерированный код (при сборке):** DTO и Feign-интерфейс в пакете `ru.dkalchenko.device.api`:
  - `ru.dkalchenko.device.api.model`: `Device`, `DeviceRequest`, `CreateResponse`, `MessageResponse`
  - `ru.dkalchenko.device.api.DefaultApi` — Feign-клиент (методы `createDevice`, `getDevice`, `updateDevice`, `deleteDevice`).

## Использование

### Подключение из Nexus

```kotlin
repositories {
    maven {
        url = uri("http://localhost:8082/repository/maven-releases/")
        isAllowInsecureProtocol = true
        credentials { ... }
    }
}

dependencies {
    implementation("ru.dkalchenko:device-service-api:1.0.0")
}
```

### Подключение как подмодуль

```kotlin
dependencies {
    implementation(project(":device-service-api"))
}
```

### Использование Feign-клиента

В приложении с Spring Cloud OpenFeign:

```java
@EnableFeignClients(basePackageClasses = DefaultApi.class)
@Configuration
public class DeviceClientConfig {}
```

Инжектить `DefaultApi`, при необходимости задать базовый URL через бин `ApiClient` или конфигурацию.

## Публикация в Nexus

При сборке артефакт публикуется в Nexus (URL и учётные данные — из свойств/переменных окружения):

```bash
./gradlew :device-service-api:publish
```

Свойства: `nexus.url`, `nexus.user`, `nexus.password` (по умолчанию — localhost:8082, admin/admin).
