module router-management-service

go 1.25.0

require (
	github.com/coreos/go-oidc/v3 v3.11.0 // OpenID Connect: проверка JWT и метаданных gRPC против issuer (OIDC discovery).
	github.com/gin-gonic/gin v1.10.0 // HTTP-маршрутизатор: эндпоинты /health и /ready поверх того же процесса, что и gRPC.
	github.com/google/uuid v1.6.0 // Генерация и парсинг UUID для сущностей в БД и API.
	github.com/jackc/pgx/v5 v5.7.4 // Драйвер и пул соединений PostgreSQL (запросы, миграции, типы).
	github.com/joho/godotenv v1.5.1 // Опциональная подгрузка переменных из .env при локальном запуске.
	github.com/robfig/cron/v3 v3.0.1 // Планировщик по cron-выражению (фоновые задачи по расписанию).
	github.com/stretchr/testify v1.11.1 // Утилиты тестирования: assert, require, моки (suite при необходимости).
	github.com/testcontainers/testcontainers-go v0.41.0 // Интеграционные тесты: поднятие Docker-контейнеров из Go.
	github.com/testcontainers/testcontainers-go/modules/postgres v0.41.0 // Готовый модуль Testcontainers для образа PostgreSQL.
	go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin v0.56.0 // HTTP: спаны запросов Gin (/health, /ready).
	go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc v0.56.0 // gRPC: серверные спаны и метаданные трассировки.
	go.opentelemetry.io/otel v1.42.0 // OpenTelemetry API: TracerProvider, propagation.
	go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp v1.42.0 // Экспорт трейсов по OTLP/HTTP в Alloy/Tempo.
	go.opentelemetry.io/otel/sdk v1.42.0 // SDK: batcher, resource для трейсов.
	google.golang.org/grpc v1.79.2 // gRPC: сервер, клиент, interceptors, коды статусов, reflection.
	google.golang.org/protobuf v1.36.11 // Runtime для сгенерированного из .proto кода: marshal/unmarshal, proto API.
	gopkg.in/yaml.v3 v3.0.1 // Разбор и сериализация YAML-конфигурации сервиса.
)

// Транзитивные зависимости: в коде сервиса не импортируются напрямую, их подтягивают перечисленные выше модули.
// Комментарии к строкам ниже — для читателя; у самих require оставлен только маркер // indirect (иначе go mod tidy дублирует текст).
require (
	// dario.cat/mergo — слияние вложенных структур (map/struct); цепочка Docker/Testcontainers и др.
	dario.cat/mergo v1.0.2 // indirect
	// github.com/Azure/go-ansiterm — эмуляция ANSI-терминала (вывод клиента Docker, в т.ч. на Windows).
	github.com/Azure/go-ansiterm v0.0.0-20250102033503-faa5f7b0171c // indirect
	// github.com/Microsoft/go-winio — именованные пайпы и низкоуровневый Win32 I/O для Docker на Windows.
	github.com/Microsoft/go-winio v0.6.2 // indirect
	// github.com/bytedance/sonic — быстрый JSON-кодек; один из движков сериализации в Gin.
	github.com/bytedance/sonic v1.15.0 // indirect
	// github.com/bytedance/sonic/loader — загрузчик/плагины для пакета sonic.
	github.com/bytedance/sonic/loader v0.5.0 // indirect
	// github.com/cenkalti/backoff/v4 — повтор запросов с нарастающей задержкой (вызовы Docker API).
	github.com/cenkalti/backoff/v4 v4.3.0 // indirect
	// github.com/cespare/xxhash/v2 — быстрый некриптографический хеш xxHash (внутренности зависимостей).
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	// github.com/cloudwego/base64x — ускоренный base64 в пайплайне sonic/JSON.
	github.com/cloudwego/base64x v0.1.6 // indirect
	// github.com/containerd/errdefs — типизированные ошибки OCI/containerd (слой контейнеров).
	github.com/containerd/errdefs v1.0.0 // indirect
	// github.com/containerd/errdefs/pkg — дополнительные обёртки вокруг errdefs.
	github.com/containerd/errdefs/pkg v0.3.0 // indirect
	// github.com/containerd/log — логирование в стиле containerd для компонентов рантайма.
	github.com/containerd/log v0.1.0 // indirect
	// github.com/containerd/platforms — разбор платформы образа (ОС/архитектура).
	github.com/containerd/platforms v0.2.1 // indirect
	// github.com/cpuguy83/dockercfg — чтение ~/.docker/config.json и связанных путей.
	github.com/cpuguy83/dockercfg v0.3.2 // indirect
	// github.com/davecgh/go-spew — подробный вывод значений в тестах (используется testify).
	github.com/davecgh/go-spew v1.1.1 // indirect
	// github.com/distribution/reference — парсинг ссылок на образы Docker (registry/name:tag@digest).
	github.com/distribution/reference v0.6.0 // indirect
	// github.com/docker/docker — клиент Docker Engine API; основа Testcontainers.
	github.com/docker/docker v28.5.2+incompatible // indirect
	// github.com/docker/go-connections — прокси, TLS и сокеты для HTTP-клиента к Docker.
	github.com/docker/go-connections v0.6.0 // indirect
	// github.com/docker/go-units — человекочитаемые единицы (байты, CPU) в сообщениях Docker.
	github.com/docker/go-units v0.5.0 // indirect
	// github.com/ebitengine/purego — вызовы нативных API без cgo (кроссплатформенные метрики хоста).
	github.com/ebitengine/purego v0.10.0 // indirect
	// github.com/felixge/httpsnoop — обёртка http.Handler для корректной инструментации OpenTelemetry.
	github.com/felixge/httpsnoop v1.0.4 // indirect
	// github.com/gabriel-vasile/mimetype — определение MIME по содержимому (валидация тел запросов в Gin).
	github.com/gabriel-vasile/mimetype v1.4.13 // indirect
	// github.com/gin-contrib/sse — Server-Sent Events; низкоуровневая поддержка в Gin.
	github.com/gin-contrib/sse v1.1.0 // indirect
	// github.com/go-jose/go-jose/v4 — JWS/JWE/JWT (стандарт JOSE); нужен github.com/coreos/go-oidc для разбора токенов.
	github.com/go-jose/go-jose/v4 v4.1.3 // indirect
	// github.com/go-logr/logr — абстракция логгера для OpenTelemetry и смежных библиотек.
	github.com/go-logr/logr v1.4.3 // indirect
	// github.com/go-logr/stdr — мост logr → стандартный пакет log.
	github.com/go-logr/stdr v1.2.2 // indirect
	// github.com/go-ole/go-ole — COM/OLE на Windows (цепочка WMI/системных запросов).
	github.com/go-ole/go-ole v1.2.6 // indirect
	// github.com/go-playground/locales — данные локалей для сообщений валидации (validator/v10).
	github.com/go-playground/locales v0.14.1 // indirect
	// github.com/go-playground/universal-translator — перевод текстов ошибок валидации полей.
	github.com/go-playground/universal-translator v0.18.1 // indirect
	// github.com/go-playground/validator/v10 — валидация struct по тегам для binding в Gin.
	github.com/go-playground/validator/v10 v10.30.1 // indirect
	// github.com/goccy/go-json — альтернативный JSON-движок в цепочке Gin.
	github.com/goccy/go-json v0.10.5 // indirect
	// github.com/grpc-ecosystem/grpc-gateway/v2 — HTTP/JSON поверх gRPC; подтягивается экосистемой grpc/protobuf, в этом сервисе не импортируется.
	github.com/grpc-ecosystem/grpc-gateway/v2 v2.28.0 // indirect
	// github.com/jackc/pgpassfile — чтение файла ~/.pgpass для pgx.
	github.com/jackc/pgpassfile v1.0.0 // indirect
	// github.com/jackc/pgservicefile — парсинг PostgreSQL connection service file.
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	// github.com/jackc/puddle/v2 — пул ресурсов и семафоры внутри pgx v5.
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	// github.com/json-iterator/go — быстрый потоковый JSON; опциональный бэкенд в Gin.
	github.com/json-iterator/go v1.1.12 // indirect
	// github.com/klauspost/compress — сжатие потоков (слои образов, архивы) в Docker-клиенте.
	github.com/klauspost/compress v1.18.2 // indirect
	// github.com/klauspost/cpuid/v2 — определение возможностей CPU для SIMD-веток (sonic и др.).
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	// github.com/leodido/go-urn — парсинг и валидация URN в validator/v10.
	github.com/leodido/go-urn v1.4.0 // indirect
	// github.com/lufia/plan9stats — статистика в духе Plan 9 для gopsutil.
	github.com/lufia/plan9stats v0.0.0-20211012122336-39d0f177ccd0 // indirect
	// github.com/magiconair/properties — формат Java .properties в утилитах вокруг контейнеров.
	github.com/magiconair/properties v1.8.10 // indirect
	// github.com/mattn/go-isatty — проверка, является ли stdout терминалом (режимы вывода Gin).
	github.com/mattn/go-isatty v0.0.20 // indirect
	// github.com/moby/docker-image-spec — спецификация образа Docker (манифесты OCI).
	github.com/moby/docker-image-spec v1.3.1 // indirect
	// github.com/moby/go-archive — tar/cpio при разборе слоёв образов.
	github.com/moby/go-archive v0.2.0 // indirect
	// github.com/moby/patternmatcher — шаблоны путей (.dockerignore и т.п.).
	github.com/moby/patternmatcher v0.6.0 // indirect
	// github.com/moby/sys/sequential — корректный последовательный доступ к файлам на Windows в контейнерном стеке.
	github.com/moby/sys/sequential v0.6.0 // indirect
	// github.com/moby/sys/user — пользователи/группы в user namespace контейнеров.
	github.com/moby/sys/user v0.4.0 // indirect
	// github.com/moby/sys/userns — поддержка user namespaces в Moby.
	github.com/moby/sys/userns v0.1.0 // indirect
	// github.com/moby/term — TTY и размер терминала для интерактивных сессий Docker.
	github.com/moby/term v0.5.2 // indirect
	// github.com/modern-go/concurrent — примитивы параллелизма для json-iterator.
	github.com/modern-go/concurrent v0.0.0-20180306012644-bacd9c7ef1dd // indirect
	// github.com/modern-go/reflect2 — быстрая рефлексия для json-iterator.
	github.com/modern-go/reflect2 v1.0.2 // indirect
	// github.com/morikuni/aec — ANSI escape-последовательности для прогресс-баров в терминале (Docker).
	github.com/morikuni/aec v1.0.0 // indirect
	// github.com/opencontainers/go-digest — идентификаторы digest слоёв OCI (sha256:…).
	github.com/opencontainers/go-digest v1.0.0 // indirect
	// github.com/opencontainers/image-spec — спецификация OCI image (config, слои, манифест).
	github.com/opencontainers/image-spec v1.1.1 // indirect
	// github.com/pelletier/go-toml/v2 — парсинг TOML в зависимостях инструментов/контейнеров.
	github.com/pelletier/go-toml/v2 v2.2.4 // indirect
	// github.com/pkg/errors — обёртки ошибок со стеком (legacy в цепочке Docker и др.).
	github.com/pkg/errors v0.9.1 // indirect
	// github.com/pmezard/go-difflib — сравнение строк в assert (testify).
	github.com/pmezard/go-difflib v1.0.0 // indirect
	// github.com/power-devops/perfstat — метрики AIX для gopsutil.
	github.com/power-devops/perfstat v0.0.0-20240221224432-82ca36839d55 // indirect
	// github.com/shirou/gopsutil/v4 — CPU, память, диск хоста; проверки ресурсов и health в Testcontainers.
	github.com/shirou/gopsutil/v4 v4.26.2 // indirect
	// github.com/sirupsen/logrus — структурированные логи; часто используется клиентом Docker.
	github.com/sirupsen/logrus v1.9.3 // indirect
	// github.com/tklauser/go-sysconf — обёртки над sysconf (лимиты ОС) для gopsutil.
	github.com/tklauser/go-sysconf v0.3.16 // indirect
	// github.com/tklauser/numcpus — число доступных CPU для gopsutil.
	github.com/tklauser/numcpus v0.11.0 // indirect
	// github.com/twitchyliquid64/golang-asm — ассемблерные заглушки под sonic и целевые архитектуры.
	github.com/twitchyliquid64/golang-asm v0.15.1 // indirect
	// github.com/ugorji/go/codec — кодек msgpack/cbor как альтернатива JSON в Gin.
	github.com/ugorji/go/codec v1.3.1 // indirect
	// github.com/yusufpapurcu/wmi — WMI на Windows для системной статистики (gopsutil).
	github.com/yusufpapurcu/wmi v1.2.4 // indirect
	// go.opentelemetry.io/auto/sdk — автоконфигурация OpenTelemetry SDK.
	go.opentelemetry.io/auto/sdk v1.2.1 // indirect
	// go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp — трассировка исходящего HTTP (клиент Docker → daemon).
	go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp v0.49.0 // indirect
	// go.opentelemetry.io/otel/metric — счётчики и гистограммы в OpenTelemetry.
	go.opentelemetry.io/otel/metric v1.42.0 // indirect
	// go.opentelemetry.io/otel/trace — спаны и контекст трассировки.
	go.opentelemetry.io/otel/trace v1.42.0 // indirect
	// golang.org/x/arch — сведения об архитектурах CPU для ассемблерных оптимизаций JSON.
	golang.org/x/arch v0.24.0 // indirect
	// golang.org/x/crypto — криптопримитивы (SCRAM для PostgreSQL, TLS, JOSE и др.).
	golang.org/x/crypto v0.48.0 // indirect
	// golang.org/x/net — HTTP/2, DNS и прочая сетевая поддержка для gRPC и HTTP-клиентов.
	golang.org/x/net v0.51.0 // indirect
	// golang.org/x/oauth2 — клиент OAuth2; discovery и обмен кодов для go-oidc.
	golang.org/x/oauth2 v0.35.0 // indirect
	// golang.org/x/sync — errgroup, semaphore и др. для параллелизма (в т.ч. в pgx).
	golang.org/x/sync v0.19.0 // indirect
	// golang.org/x/sys — низкоуровневые вызовы ОС: файлы, сеть, время.
	golang.org/x/sys v0.41.0 // indirect
	// golang.org/x/text — Unicode и локали; используется валидацией и текстовой обработкой.
	golang.org/x/text v0.34.0 // indirect
	// google.golang.org/genproto/googleapis/rpc — сгенерированные типы для gRPC Status и кодов ошибок Google RPC.
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260226221140-a57be14db171 // indirect
)

require github.com/prometheus/client_golang v1.23.2

require (
	github.com/beorn7/perks v1.0.1 // indirect
	github.com/bytedance/gopkg v0.1.3 // indirect
	github.com/cenkalti/backoff/v5 v5.0.3 // indirect
	github.com/munnerz/goautoneg v0.0.0-20191010083416-a7dc8b61c822 // indirect
	github.com/pashagolub/pgxmock/v4 v4.9.0 // indirect
	github.com/prometheus/client_model v0.6.2 // indirect
	github.com/prometheus/common v0.66.1 // indirect
	github.com/prometheus/procfs v0.16.1 // indirect
	go.opentelemetry.io/otel/exporters/otlp/otlptrace v1.42.0 // indirect
	go.opentelemetry.io/proto/otlp v1.9.0 // indirect
	go.yaml.in/yaml/v2 v2.4.2 // indirect
	google.golang.org/genproto/googleapis/api v0.0.0-20260209200024-4cfbd4190f57 // indirect
)
