package main

import (
	"context"
	"log/slog"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"google.golang.org/grpc"

	"router-management-service/internal/config"
	"router-management-service/internal/repository"
	"router-management-service/internal/service"
)

func TestBuildGRPCServerOptions_TracingOffAuthOff(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{}
	opts, err := buildGRPCServerOptions(context.Background(), cfg)
	require.NoError(t, err)
	require.Empty(t, opts)
}

func TestBuildGRPCServerOptions_TracingOn(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{
		Telemetry: config.TelemetryConfig{OTLPTracesEndpoint: "http://collector:4318/v1/traces"},
	}
	opts, err := buildGRPCServerOptions(context.Background(), cfg)
	require.NoError(t, err)
	require.Len(t, opts, 1)
}

func TestRegisterRouterManagementGRPC(t *testing.T) {
	t.Parallel()
	s := grpc.NewServer()
	svc := service.NewCommandService(
		&repository.MockRouterStore{},
		&repository.MockCommandStore{},
		slog.Default(),
	)
	registerRouterManagementGRPC(s, svc, slog.Default())
	s.Stop()
}

func TestNewRegisteredGRPCServer(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{}
	svc := service.NewCommandService(
		&repository.MockRouterStore{},
		&repository.MockCommandStore{},
		slog.Default(),
	)
	s, err := newRegisteredGRPCServer(context.Background(), cfg, svc, slog.Default())
	require.NoError(t, err)
	require.NotNil(t, s)
	s.Stop()
}

func TestBuildGRPCServerOptions_AuthEnabled_UnreachableIssuer(t *testing.T) {
	if testing.Short() {
		t.Skip("network-dependent OIDC discovery")
	}
	t.Parallel()
	cfg := &config.Config{
		Auth: config.AuthConfig{
			Enabled:   true,
			IssuerURL: "http://127.0.0.1:1/realms/unreachable",
		},
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	_, err := buildGRPCServerOptions(ctx, cfg)
	require.Error(t, err)
}
