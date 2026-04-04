package main

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/grpc"
	"router-management-service/internal/config"
	"router-management-service/internal/migrate"
	"router-management-service/internal/repository"
	"router-management-service/internal/service"
	"router-management-service/internal/telemetry"
)

// appRuntime — поднятые зависимости и серверы после initApp (до ожидания сигнала остановки).
type appRuntime struct {
	cfg               *config.Config
	log               *slog.Logger
	pool              *pgxpool.Pool
	telemetryShutdown func(context.Context) error
	stopSched         func()
	grpcServer        *grpc.Server
	httpSrv           *http.Server
}

func (a *appRuntime) shutdownTelemetry() {
	if a.telemetryShutdown == nil {
		return
	}
	sdCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := a.telemetryShutdown(sdCtx); err != nil {
		a.log.Warn("telemetry shutdown", "err", err)
	}
}

// initApp выполняет загрузку конфигурации, телеметрию, PostgreSQL, миграции, планировщик, gRPC/HTTP-серверы.
// При ошибке освобождает уже выделенные ресурсы (pool, OTLP).
func initApp(ctx context.Context, log *slog.Logger) (*appRuntime, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}

	telemetryShutdown, err := telemetry.Setup(ctx, cfg.Telemetry.OTLPTracesEndpoint, cfg.Telemetry.ServiceName)
	if err != nil {
		return nil, err
	}
	cleanupTel := func() {
		sdCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = telemetryShutdown(sdCtx)
	}

	logTracingIfEnabled(cfg, log)

	pool, err := pgxpool.New(ctx, cfg.PostgresDSN())
	if err != nil {
		cleanupTel()
		return nil, err
	}
	cleanupPool := func() { pool.Close() }

	if err := pool.Ping(ctx); err != nil {
		cleanupPool()
		cleanupTel()
		return nil, err
	}

	if err := migrate.Run(ctx, pool, log); err != nil {
		cleanupPool()
		cleanupTel()
		return nil, err
	}

	routerStore := repository.NewPostgresRouterStore(pool)
	commandStore := repository.NewPostgresCommandStore(pool)
	svc := service.NewCommandService(routerStore, commandStore, log)

	stopSched, schedErr := startFailStaleSentScheduler(cfg, svc, log)
	if schedErr != nil {
		log.Error("init scheduler", "err", schedErr)
		stopSched = func() {}
	} else if cfg.Scheduler.Enabled && cfg.Scheduler.SentTimeoutMinutes > 0 && cfg.Scheduler.Cron != "" {
		log.Info("scheduler started",
			"cron", cfg.Scheduler.Cron,
			"sent_timeout_minutes", cfg.Scheduler.SentTimeoutMinutes,
		)
	}

	grpcServer, err := newRegisteredGRPCServer(ctx, cfg, svc, log)
	if err != nil {
		stopSched()
		cleanupPool()
		cleanupTel()
		return nil, err
	}

	if cfg.Auth.Enabled && cfg.Auth.IssuerURL != "" {
		log.Info("auth enabled", "issuer", cfg.Auth.IssuerURL, "client_id", cfg.Auth.ClientID)
	}

	httpRouter := newHTTPRouter(cfg, pool, log)
	httpSrv := &http.Server{Addr: cfg.HTTPAddr(), Handler: httpRouter}

	grpcListener, err := net.Listen("tcp", cfg.GRPCAddr())
	if err != nil {
		stopSched()
		cleanupPool()
		cleanupTel()
		return nil, err
	}

	rt := &appRuntime{
		cfg:               cfg,
		log:               log,
		pool:              pool,
		telemetryShutdown: telemetryShutdown,
		stopSched:         stopSched,
		grpcServer:        grpcServer,
		httpSrv:           httpSrv,
	}

	go func() {
		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Error("http server", "err", err)
		}
	}()

	go func() {
		if err := grpcServer.Serve(grpcListener); err != nil {
			log.Error("grpc server", "err", err)
		}
	}()

	log.Info("started", "grpc", cfg.GRPCAddr(), "http", cfg.HTTPAddr())
	return rt, nil
}
