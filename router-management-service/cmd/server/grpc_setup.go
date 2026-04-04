package main

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"

	apiv1 "router-management-service/api/proto/api/v1"
	"router-management-service/internal/auth"
	"router-management-service/internal/config"
	"router-management-service/internal/grpchandler"
	"router-management-service/internal/service"
)

// buildGRPCServerOptions собирает опции gRPC-сервера (OTel stats, JWT interceptor при включённом auth).
func buildGRPCServerOptions(ctx context.Context, cfg *config.Config) ([]grpc.ServerOption, error) {
	var opts []grpc.ServerOption
	if cfg.TracingEnabled() {
		opts = append(opts, grpc.StatsHandler(otelgrpc.NewServerHandler()))
	}
	if cfg.Auth.Enabled && cfg.Auth.IssuerURL != "" {
		if strings.TrimSpace(cfg.Auth.ClientID) == "" {
			return nil, fmt.Errorf("auth is enabled but client_id is empty: set auth.client_id in config or AUTH_CLIENT_ID (JWT audience)")
		}
		authenticator, err := auth.NewJWTAuth(ctx, cfg.Auth.IssuerURL, cfg.Auth.ClientID)
		if err != nil {
			return nil, err
		}
		opts = append(opts,
			grpc.ChainUnaryInterceptor(authenticator.UnaryServerInterceptor()),
			grpc.ChainStreamInterceptor(authenticator.StreamServerInterceptor()),
		)
	}
	return opts, nil
}

// registerRouterManagementGRPC регистрирует API и gRPC reflection.
func registerRouterManagementGRPC(s *grpc.Server, svc *service.CommandService, log *slog.Logger) {
	apiv1.RegisterRouterManagementServiceServer(s, grpchandler.NewServer(svc, log))
	reflection.Register(s)
}

// newRegisteredGRPCServer создаёт gRPC-сервер с опциями из конфига и зарегистрированным RouterManagement API.
func newRegisteredGRPCServer(ctx context.Context, cfg *config.Config, svc *service.CommandService, log *slog.Logger) (*grpc.Server, error) {
	opts, err := buildGRPCServerOptions(ctx, cfg)
	if err != nil {
		return nil, err
	}
	s := grpc.NewServer(opts...)
	registerRouterManagementGRPC(s, svc, log)
	return s, nil
}
