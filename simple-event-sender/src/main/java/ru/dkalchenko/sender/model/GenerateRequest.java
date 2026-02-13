package ru.dkalchenko.sender.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Тело запроса на генерацию событий.
 */
public record GenerateRequest(
        @NotNull(message = "eventsAmt обязателен")
        @Min(value = 1, message = "eventsAmt должен быть не меньше 1")
        @Max(value = 100_000_000, message = "eventsAmt не должен превышать 100000000")
        Integer eventsAmt
) {}
