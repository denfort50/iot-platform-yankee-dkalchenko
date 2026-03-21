package main

import (
	"log/slog"
	"testing"

	"github.com/stretchr/testify/require"

	"router-management-service/internal/config"
	"router-management-service/internal/repository"
	"router-management-service/internal/service"
)

func TestStartFailStaleSentScheduler_Disabled(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{}
	svc := service.NewCommandService(
		&repository.MockRouterStore{},
		&repository.MockCommandStore{},
		slog.Default(),
	)
	stop, err := startFailStaleSentScheduler(cfg, svc, slog.Default())
	require.NoError(t, err)
	stop()
}

func TestStartFailStaleSentScheduler_InvalidCron(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{
		Scheduler: config.SchedulerConfig{
			Enabled:            true,
			Cron:               "not-a-valid-cron",
			SentTimeoutMinutes: 5,
		},
	}
	svc := service.NewCommandService(
		&repository.MockRouterStore{},
		&repository.MockCommandStore{},
		slog.Default(),
	)
	_, err := startFailStaleSentScheduler(cfg, svc, slog.Default())
	require.Error(t, err)
}

func TestStartFailStaleSentScheduler_StartsAndStops(t *testing.T) {
	t.Parallel()
	cfg := &config.Config{
		Scheduler: config.SchedulerConfig{
			Enabled:            true,
			Cron:               "0 0 1 1 *",
			SentTimeoutMinutes: 10,
		},
	}
	svc := service.NewCommandService(
		&repository.MockRouterStore{},
		&repository.MockCommandStore{},
		slog.Default(),
	)
	stop, err := startFailStaleSentScheduler(cfg, svc, slog.Default())
	require.NoError(t, err)
	stop()
}
