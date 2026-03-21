package main

import (
	"context"
	"log/slog"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestInitApp_ConfigFileMissing(t *testing.T) {
	t.Setenv("CONFIG_PATH", filepath.Join(t.TempDir(), "nonexistent-config.yaml"))
	_, err := initApp(context.Background(), slog.Default())
	require.Error(t, err)
}

func TestInitApp_PostgresUnreachable(t *testing.T) {
	if testing.Short() {
		t.Skip("dial to closed port")
	}
	t.Setenv("CONFIG_PATH", "")
	t.Setenv("POSTGRES_HOST", "127.0.0.1")
	t.Setenv("POSTGRES_PORT", "65432")
	t.Setenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "")

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	_, err := initApp(ctx, slog.Default())
	require.Error(t, err)
}
