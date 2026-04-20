package ru.dkalchenko.event.exception;

/**
 * Некорректные параметры запроса (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
