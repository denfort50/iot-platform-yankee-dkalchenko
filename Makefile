DOCKER_COMPOSE = docker-compose -f infrastructure/docker-compose.yaml
INFRA_SERVICES ?= iot-platform-postgres keycloak-postgres camunda-postgres clickhouse redis redis-insight keycloak kafka1 kafka2 kafka3 schema-registry kafka-ui minio camunda iot-platform-postgres-exporter keycloak-postgres-exporter camunda-postgres-exporter kafka-exporter redis-exporter prometheus grafana tempo loki alloy
MODULE_TWO ?= events-collector-service simple-event-sender clickhouse redis redis-insight redis-exporter kafka1 kafka2 kafka3 schema-registry kafka-ui kafka-exporter prometheus grafana tempo loki alloy

.PHONY: all stop clean logs infra infra-logs infra-stop rebuild

all:
	$(DOCKER_COMPOSE) up -d

module-two:
	$(DOCKER_COMPOSE) up -d $(MODULE_TWO)

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