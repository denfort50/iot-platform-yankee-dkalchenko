# Минимальная настройка Keycloak для device-service

Keycloak и keycloak-postgres должны быть запущены (например, через `docker compose up keycloak keycloak-postgres keycloak-init` в каталоге `infrastructure`).

## Автоматическая настройка при развёртывании

При старте контейнеров запускается сервис **keycloak-init**: он ждёт готовности Keycloak, через Admin API 
создаёт тестового пользователя и включает Direct access grants для клиента **admin-cli**.

Учётные данные тестового пользователя задаются в `infrastructure/.env`:
- `KEYCLOAK_TEST_USERNAME` (по умолчанию `device-api-user`)
- `KEYCLOAK_TEST_PASSWORD` (по умолчанию `device-api-password`)

## Ручная настройка (если keycloak-init не используется)

- Админ-консоль: **http://localhost:8080**, логин/пароль из `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`.
- Realm: **master** (достаточно встроенного).
- Пользователь: **Users** → **Add user** → задать username и пароль во вкладке **Credentials**.
- Клиент: **Clients** → **admin-cli** → включить **Direct access grants** → Save.

## Получение JWT (токена)

**device-service на хосте (localhost):** получай токен с `http://localhost:8080`:

```bash

curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=device-api-user" \
  -d "password=device-api-password" \
  -d "grant_type=password" \
  -d "client_id=admin-cli"
```

**device-service в Docker:** issuer в токене должен совпадать с настройкой приложения. В compose задан `KC_HOSTNAME=host.docker.internal`, поэтому токен нужно брать с этого же хоста (иначе будет ошибка «The iss claim is not valid»):

```bash

curl -s -X POST "http://host.docker.internal:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=device-api-user" \
  -d "password=device-api-password" \
  -d "grant_type=password" \
  -d "client_id=admin-cli"
```

В ответе поле **access_token** — это JWT для заголовка `Authorization`. Вызов API device-service в Docker: `http://localhost:8097` с заголовком `Authorization: Bearer <access_token>`.

## Конфигурация device-service

| Запуск             | KEYCLOAK_ISSUER_URI                              |
|--------------------|--------------------------------------------------|
| Локально (с хоста) | `http://localhost:8080/realms/master`            |
| В Docker (compose) | `http://host.docker.internal:8080/realms/master` |

В Docker Keycloak запущен с `KC_HOSTNAME=host.docker.internal`, чтобы issuer в JWT был одинаковым при запросе с хоста и из контейнера. device-service в compose использует тот же issuer (через `KEYCLOAK_ISSUER_URI` или дефолт в `application-docker.yaml`).

## Проверка

- Без токена: `curl -s http://localhost:8097/api/v1/devices` (или 8081 при локальном запуске) → **401 Unauthorized**.
- С валидным токеном: тот же URL с `Authorization: Bearer <access_token>` → **200**.

Если видишь «The iss claim is not valid» — токен получен с другого хоста (например, с localhost), чем указан в `issuer-uri` (например, host.docker.internal). Получай токен с того же URL, что и issuer (при Docker — с `http://host.docker.internal:8080`).
