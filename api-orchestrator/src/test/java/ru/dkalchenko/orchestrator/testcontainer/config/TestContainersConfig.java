package ru.dkalchenko.orchestrator.testcontainer.config;

import org.testcontainers.containers.Network;

public final class TestContainersConfig {

	public static final Network NETWORK = Network.newNetwork();

	private TestContainersConfig() {
	}
}
