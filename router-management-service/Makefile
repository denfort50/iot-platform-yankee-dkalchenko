# Makefile для router-management-service: сборка, регенерация proto, тесты, запуск.
.PHONY: build generate test test-integration run cover-html cover-html-nice smoke
MODULE = router-management-service

# Сборка бинарника в корне проекта.
build:
	go build -o $(MODULE).exe ./cmd/server

# Регенерация Go-кода из api/proto/api/v1/router_management.proto (нужен protoc, protoc-gen-go, protoc-gen-go-grpc).
generate:
	protoc --go_out=. --go_opt=module=$(MODULE) --go-grpc_out=. --go-grpc_opt=module=$(MODULE) api/proto/api/v1/router_management.proto

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
	go tool cover -html coverage.out -o coverage.html

# HTML-покрытие: отчёт через htmlcov (таблица пакетов, общий %, аккуратная вёрстка). Нужен бинарник: go install github.com/essentialkaos/htmlcov@latest
# Результат: coverage-htmlcov.html и coverage.out
cover-html-nice:
	go test ./... -coverprofile=coverage.out -covermode=atomic
	htmlcov -o coverage-htmlcov.html coverage.out
