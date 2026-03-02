DOCKER_COMPOSE = docker-compose -f infrastructure/docker-compose.yaml
NEXUS_URL = http://localhost:8082
INFRA_SERVICES ?= keycloak keycloak-postgres keycloak-init keycloak-postgres-exporter camunda camunda-postgres camunda-postgres-exporter clickhouse redis redis-insight redis-exporter kafka1 kafka2 kafka3 schema-registry kafka-ui kafka-exporter postgres-shard0 postgres-shard1 postgres-shard2 iot-platform-postgres-exporter-shard0 iot-platform-postgres-exporter-shard1 iot-platform-postgres-exporter-shard2 nexus minio prometheus grafana tempo loki alloy
MODULE_FIVE ?= failed-events-processor device-service device-collector-service events-collector-service simple-event-sender clickhouse redis redis-insight redis-exporter kafka1 kafka2 kafka3 schema-registry kafka-ui kafka-exporter prometheus grafana tempo loki alloy postgres-shard0 postgres-shard1 postgres-shard2 iot-platform-postgres-exporter-shard0 iot-platform-postgres-exporter-shard1 iot-platform-postgres-exporter-shard2 nexus keycloak keycloak-postgres keycloak-init keycloak-postgres-exporter device-service-lb minio

.PHONY: all stop clean logs infra infra-logs infra-stop rebuild device-service-image device-collector-service-image events-collector-service-image simple-event-sender-image failed-events-processor-image

all: up build-artifacts start

ifeq ($(OS),Windows_NT)
GRADLE = .\gradlew.bat
NULL_DEV = NUL
WAIT_CMD = powershell -Command "while ($$true) { \
		try { \
			Invoke-WebRequest -UseBasicParsing -Uri $(NEXUS_URL)/service/rest/v1/status -ErrorAction Stop; \
			break \
		} \
		catch { \
			Write-Host 'Nexus not ready, sleeping...'; \
			Start-Sleep -Seconds 10 \
		} \
	}"
else
GRADLE = ./gradlew
NULL_DEV = /dev/null
WAIT_CMD = until curl -sf $(NEXUS_URL)/service/rest/v1/status; do \
echo 'Nexus not ready, sleeping...'; sleep 5; \
done
endif

up:
	$(DOCKER_COMPOSE) up -d kafka1 kafka2 kafka3 schema-registry nexus
	@echo "Waiting for Nexus, Kafka and Schema Registry to be healthy..."
	@$(WAIT_CMD)
	@echo "Nexus is healthy!"

build-artifacts:
	@echo "Building iot-avro-schemas (register schemas + publish to Nexus)..."
	@$(GRADLE) :iot-avro-schemas:build
	@echo "Building device-service-api (publish to Nexus)..."
	@$(GRADLE) :device-service-api:build

start:
	$(DOCKER_COMPOSE) up -d

module-five: up build-artifacts device-service-image device-collector-service-image events-collector-service-image simple-event-sender-image failed-events-processor-image
	$(DOCKER_COMPOSE) up -d $(MODULE_FIVE)

stop:
	$(DOCKER_COMPOSE) stop

clean:
	$(DOCKER_COMPOSE) down -v

logs:
	$(DOCKER_COMPOSE) logs -f --tail=200

infra:
	@echo "Starting infrastructure services: $(INFRA_SERVICES)"
	$(DOCKER_COMPOSE) up -d $(INFRA_SERVICES)
	@echo "Infrastructure is ready."

infra-logs:
	$(DOCKER_COMPOSE) logs -f --tail=200 $(INFRA_SERVICES)

infra-stop:
	$(DOCKER_COMPOSE) stop $(INFRA_SERVICES)

rebuild: clean all

# Сборка JAR на хосте и образы сервисов (без Gradle/Nexus внутри Docker).
# Образ собирается только если образа с данной версией ещё нет.
device-service-image:
	@docker image inspect device-service:1.0.0 >$(NULL_DEV) 2>&1 && echo "Image device-service:1.0.0 already exists, skipping." || ( \
		echo "Building device-service JAR on host..." && \
		$(GRADLE) :device-service:clean :device-service:bootJar && \
		echo "Building Docker image device-service:1.0.0..." && \
		docker build -f device-service/Dockerfile -t device-service:1.0.0 . \
	)

device-collector-service-image:
	@docker image inspect device-collector-service:1.0.0 >$(NULL_DEV) 2>&1 && echo "Image device-collector-service:1.0.0 already exists, skipping." || ( \
		echo "Building device-collector-service JAR on host..." && \
		$(GRADLE) :device-collector-service:clean :device-collector-service:bootJar && \
		echo "Building Docker image device-collector-service:1.0.0..." && \
		docker build -f device-collector-service/Dockerfile -t device-collector-service:1.0.0 . \
	)

events-collector-service-image:
	@docker image inspect events-collector-service:1.0.0 >$(NULL_DEV) 2>&1 && echo "Image events-collector-service:1.0.0 already exists, skipping." || ( \
		echo "Building events-collector-service JAR on host..." && \
		$(GRADLE) :events-collector-service:clean :events-collector-service:bootJar && \
		echo "Building Docker image events-collector-service:1.0.0..." && \
		docker build -f events-collector-service/Dockerfile -t events-collector-service:1.0.0 . \
	)

simple-event-sender-image:
	@docker image inspect simple-event-sender:1.0.0 >$(NULL_DEV) 2>&1 && echo "Image simple-event-sender:1.0.0 already exists, skipping." || ( \
		echo "Building simple-event-sender JAR on host..." && \
		$(GRADLE) :simple-event-sender:clean :simple-event-sender:bootJar && \
		echo "Building Docker image simple-event-sender:1.0.0..." && \
		docker build -f simple-event-sender/Dockerfile -t simple-event-sender:1.0.0 . \
	)

failed-events-processor-image:
	@docker image inspect failed-events-processor:1.0.0 >$(NULL_DEV) 2>&1 && echo "Image failed-events-processor:1.0.0 already exists, skipping." || ( \
		echo "Building failed-events-processor JAR on host..." && \
		$(GRADLE) :failed-events-processor:clean :failed-events-processor:bootJar && \
		echo "Building Docker image failed-events-processor:1.0.0..." && \
		docker build -f failed-events-processor/Dockerfile -t failed-events-processor:1.0.0 . \
	)