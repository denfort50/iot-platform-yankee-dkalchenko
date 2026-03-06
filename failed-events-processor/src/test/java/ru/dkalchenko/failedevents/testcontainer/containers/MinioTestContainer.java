package ru.dkalchenko.failedevents.testcontainer.containers;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@UtilityClass
public class MinioTestContainer {

	public static final GenericContainer<?> INSTANCE = new GenericContainer<>("minio/minio:RELEASE.2024-05-28T17-19-04Z")
			.withEnv("MINIO_ROOT_USER", "minioadmin")
			.withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
			.withCommand("server /data --console-address :9001")
			.withExposedPorts(9000)
			.waitingFor(Wait.forListeningPort());
}
