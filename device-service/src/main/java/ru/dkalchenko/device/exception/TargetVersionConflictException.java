package ru.dkalchenko.device.exception;

/**
 * Конфликт при фиксации целевой версии (идемпотентность или несовместимое состояние).
 */
public class TargetVersionConflictException extends RuntimeException {

	public TargetVersionConflictException(String message) {
		super(message);
	}
}
