package ru.dkalchenko.orchestrator.application.model;

import java.util.List;

public record VersionUpdateResult(boolean compensated, String message, List<String> createdCommandIds) {
}
