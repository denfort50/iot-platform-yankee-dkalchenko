package ru.dkalchenko.orchestrator.application.exception;

public class RouterNotFoundException extends RuntimeException {

	public RouterNotFoundException(String routerId) {
		super("Router not found: " + routerId);
	}
}
