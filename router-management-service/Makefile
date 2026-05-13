# Makefile для router-management-service: сборка, регенерация proto, тесты, запуск.
.PHONY: build generate proto-from-nexus clean-proto-cache test test-integration run cover-html cover-html-nice smoke
MODULE = router-management-service

ROUTER_API_GROUP = ru/dkalchenko
ROUTER_API_ARTIFACT = router-management-api
ROUTER_API_VERSION ?= 1.0.0
NEXUS_URL ?= http://localhost:8082/repository/maven-releases
NEXUS_USER ?= admin
NEXUS_PASSWORD ?= admin

PROTO_CACHE_DIR = .proto-cache
PROTO_JAR = $(PROTO_CACHE_DIR)/$(ROUTER_API_ARTIFACT)-$(ROUTER_API_VERSION).jar
PROTO_FILE_IN_JAR = proto/api/v1/router_management.proto
PROTO_FILE = $(PROTO_CACHE_DIR)/api/v1/router_management.proto
PROTO_DOWNLOAD_URL = $(NEXUS_URL)/$(ROUTER_API_GROUP)/$(ROUTER_API_ARTIFACT)/$(ROUTER_API_VERSION)/$(ROUTER_API_ARTIFACT)-$(ROUTER_API_VERSION).jar

# Сборка бинарника в корне проекта.
build:
	go build -o $(MODULE).exe ./cmd/server

ifeq ($(OS),Windows_NT)
PROTO_FETCH_AND_EXTRACT = powershell -NoProfile -Command "$$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '$(PROTO_CACHE_DIR)' | Out-Null; $$pair = '$(NEXUS_USER):$(NEXUS_PASSWORD)'; $$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($$pair)); $$headers = @{ Authorization = 'Basic ' + $$basic }; Invoke-WebRequest -UseBasicParsing -Headers $$headers -Uri '$(PROTO_DOWNLOAD_URL)' -OutFile '$(PROTO_JAR)'; Add-Type -AssemblyName System.IO.Compression.FileSystem; if (Test-Path '$(PROTO_FILE)') { Remove-Item -Force '$(PROTO_FILE)' }; [IO.Compression.ZipFile]::ExtractToDirectory('$(PROTO_JAR)', '$(PROTO_CACHE_DIR)', $$true); if (-not (Test-Path '$(PROTO_FILE)')) { throw 'Proto file was not found in downloaded artifact: $(PROTO_FILE_IN_JAR)' }"
else
PROTO_FETCH_AND_EXTRACT = mkdir -p $(PROTO_CACHE_DIR) && curl -fsSL -u $(NEXUS_USER):$(NEXUS_PASSWORD) "$(PROTO_DOWNLOAD_URL)" -o "$(PROTO_JAR)" && unzip -oj "$(PROTO_JAR)" "$(PROTO_FILE_IN_JAR)" -d "$(PROTO_CACHE_DIR)/api/v1"
endif

# Скачивание proto-контракта из Nexus и распаковка во временный кэш.
proto-from-nexus:
	$(PROTO_FETCH_AND_EXTRACT)

# Регенерация Go-кода из proto, скачанного из Nexus (нужны protoc, protoc-gen-go, protoc-gen-go-grpc).
generate: proto-from-nexus
	protoc --go_out=. --go_opt=module=$(MODULE) --go-grpc_out=. --go-grpc_opt=module=$(MODULE) -I $(PROTO_CACHE_DIR) $(PROTO_FILE)

# Очистка локального кэша proto-артефактов.
clean-proto-cache:
ifeq ($(OS),Windows_NT)
	if exist "$(PROTO_CACHE_DIR)" rmdir /s /q "$(PROTO_CACHE_DIR)"
else
	rm -rf "$(PROTO_CACHE_DIR)"
endif

# Запуск всех тестов (в т.ч. internal/service с mock-хранилищами). Интеграционные тесты не запускаются (см. test-integration).
test:
	go test ./...

# Интеграционные тесты с Testcontainers (PostgreSQL в Docker). Требует запущенный Docker.
test-integration:
	go test -tags=integration ./integration/ -v -count=1

# Smoke-тест gRPC (полный цикл команд). Нужны grpcurl и jq; при JWT — задать JWT_TOKEN.
# Windows: см. scripts/smoke-test.ps1
smoke:
	bash scripts/smoke-test.sh

# Локальный запуск сервиса (go run).
run:
	go run ./cmd/server

# HTML-покрытие: встроенный отчёт Go (подсветка строк в исходниках; без сводной таблицы по пакетам, как в JaCoCo).
# Результат: coverage.html и coverage.out
cover-html:
	go test ./... -coverprofile=coverage.out -covermode=atomic
	go tool cover -html=coverage.out -o coverage.html

# HTML-покрытие: отчёт через htmlcov (таблица пакетов, общий %, аккуратная вёрстка). Нужен бинарник: go install github.com/essentialkaos/htmlcov@latest
# Результат: coverage-htmlcov.html и coverage.out
cover-html-nice:
	go test ./... -coverprofile=coverage.out -covermode=atomic
	htmlcov -o coverage-htmlcov.html coverage.out
