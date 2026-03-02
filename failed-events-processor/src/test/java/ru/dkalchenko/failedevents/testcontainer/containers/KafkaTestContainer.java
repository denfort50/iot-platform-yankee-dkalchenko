package ru.dkalchenko.failedevents.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import ru.dkalchenko.failedevents.testcontainer.config.TestContainersConfig;

@UtilityClass
public class KafkaTestContainer {

	public static final int EXTERNAL_PORT = 19092;

	public static final GenericContainer<?> INSTANCE = new FixedHostPortGenericContainer<>("confluentinc/cp-kafka:8.1.1")
			.withNetwork(TestContainersConfig.NETWORK)
			.withNetworkAliases("kafka")
			.withExposedPorts(9092, EXTERNAL_PORT)
			.withFixedExposedPort(EXTERNAL_PORT, EXTERNAL_PORT)
			.withEnv("KAFKA_NODE_ID", "1")
			.withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
			.withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:9093")
			.withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,EXTERNAL://0.0.0.0:" + EXTERNAL_PORT)
			.withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,EXTERNAL://localhost:" + EXTERNAL_PORT)
			.withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT")
			.withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
			.withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
			.withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
			.withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
			.withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
			.withEnv("KAFKA_MIN_INSYNC_REPLICAS", "1")
			.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
			.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));
}
