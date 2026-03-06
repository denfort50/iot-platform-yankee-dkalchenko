package ru.dkalchenko.failedevents.testcontainer.config;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.Network;

@UtilityClass
public class TestContainersConfig {

	public static final Network NETWORK = Network.newNetwork();
}
