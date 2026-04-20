package ru.dkalchenko.event.exception;

/**
 * Событие не найдено (HTTP 404).
 */
public class EventNotFoundException extends RuntimeException {

	public EventNotFoundException(String message) {
		super(message);
	}
}
