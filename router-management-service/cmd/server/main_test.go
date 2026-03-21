package main

import (
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/require"

	"router-management-service/internal/config"
)

func TestNewHTTPRouter_HealthAndMetrics(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{}
	r := newHTTPRouter(cfg, nil, slog.Default())

	for _, path := range []string{"/health", "/metrics"} {
		path := path
		t.Run(path, func(t *testing.T) {
			t.Parallel()
			w := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, path, nil)
			r.ServeHTTP(w, req)
			if w.Code != http.StatusOK {
				t.Fatalf("%s: status %d body %s", path, w.Code, w.Body.String())
			}
		})
	}
}

func TestLogTracingIfEnabled(t *testing.T) {
	t.Parallel()
	log := slog.Default()
	logTracingIfEnabled(&config.Config{}, log)
	cfg := &config.Config{
		Telemetry: config.TelemetryConfig{OTLPTracesEndpoint: "http://localhost:4318/v1/traces"},
	}
	logTracingIfEnabled(cfg, log)
}

func TestNewHTTPRouter_TracingMiddleware(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{
		Telemetry: config.TelemetryConfig{
			OTLPTracesEndpoint: "http://localhost:4318/v1/traces",
			ServiceName:        "test-svc",
		},
	}
	r := newHTTPRouter(cfg, nil, slog.Default())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/health", nil))
	require.Equal(t, http.StatusOK, w.Code)
}
