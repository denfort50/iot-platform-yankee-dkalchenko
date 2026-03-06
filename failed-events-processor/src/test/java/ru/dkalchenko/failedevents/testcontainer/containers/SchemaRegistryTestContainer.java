package ru.dkalchenko.failedevents.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import ru.dkalchenko.failedevents.testcontainer.config.TestContainersConfig;

@UtilityClass
public class SchemaRegistryTestContainer {

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>("confluentinc/cp-schema-registry:8.1.1")
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("schema-registry")
			.withExposedPorts(8081)
			.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
			.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
			.dependsOn(KafkaTestContainer.INSTANCE)
			.waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
}
