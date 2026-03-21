package main

import (
	"log/slog"

	"router-management-service/internal/config"
)

func logTracingIfEnabled(cfg *config.Config, log *slog.Logger) {
	if cfg.TracingEnabled() {
		log.Info("tracing enabled", "otlp_traces", cfg.Telemetry.OTLPTracesEndpoint, "service", cfg.TelemetryServiceName())
	}
}
