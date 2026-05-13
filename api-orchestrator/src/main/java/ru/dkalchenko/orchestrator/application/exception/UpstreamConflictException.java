package ru.dkalchenko.orchestrator.application.exception;

/**
 * Восходящий сервис вернул 409 Conflict.
 */
public class UpstreamConflictException extends RuntimeException {

	public UpstreamConflictException(String message) {
		super(message);
	}
}
