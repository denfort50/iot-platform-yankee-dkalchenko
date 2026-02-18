package ru.dkalchenko.device.collector.testcontainer.config;

import lombok.experimental.UtilityClass;
import org.testcontainers.containers.Network;

@UtilityClass
public class TestContainersConfig {

	public static final Network NETWORK = Network.newNetwork();
}
