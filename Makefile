DOCKER_COMPOSE = docker-compose -f infrastructure/docker-compose.yaml
NEXUS_URL = http://localhost:8082
INFRA_SERVICES ?= keycloak keycloak-postgres keycloak-postgres-exporter camunda camunda-postgres camunda-postgres-exporter clickhouse redis redis-insight redis-exporter kafka1 kafka2 kafka3 schema-registry kafka-ui kafka-exporter postgres-shard0 postgres-shard1 postgres-shard2 iot-platform-postgres-exporter-shard0 iot-platform-postgres-exporter-shard1 iot-platform-postgres-exporter-shard2 nexus minio prometheus grafana tempo loki alloy
MODULE_THREE ?= device-collector-service events-collector-service simple-event-sender clickhouse redis kafka1 kafka2 kafka3 schema-registry kafka-ui prometheus grafana tempo loki alloy postgres-shard0 postgres-shard1 postgres-shard2 nexus

.PHONY: all stop clean logs infra infra-logs infra-stop rebuild

all: up build-artifacts start

ifeq ($(OS),Windows_NT)
GRADLE = .\gradlew.bat
WAIT_CMD = powershell -Command "while ($$true) { \
		try { \
			Invoke-WebRequest -UseBasicParsing -Uri $(NEXUS_URL)/service/rest/v1/status -ErrorAction Stop; \
			break \
		} \
		catch { \
			Write-Host 'Nexus not ready, sleeping...'; \
			Start-Sleep -Seconds 5 \
		} \
	}"
else
GRADLE = ./gradlew
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

start:
	$(DOCKER_COMPOSE) up -d

module-three: up build-artifacts
	$(DOCKER_COMPOSE) up -d $(MODULE_THREE)

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