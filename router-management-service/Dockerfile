# Сборка сервиса управления роутерами: многостадийный образ (сборка на Go, запуск на Alpine).
# Этап сборки: загрузка зависимостей и компиляция бинарника из cmd/server.
FROM golang:1.25-alpine AS builder
WORKDIR /app
RUN apk add --no-cache git ca-certificates
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /router-management-service ./cmd/server

# Этап запуска: минимальный образ с бинарником; порты 50051 (gRPC) и 8080 (HTTP).
FROM alpine:3.20
RUN apk add --no-cache ca-certificates wget
WORKDIR /app
COPY --from=builder /router-management-service .
EXPOSE 50051 8080
ENTRYPOINT ["./router-management-service"]
