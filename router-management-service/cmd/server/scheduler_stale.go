package main

import (
	"context"
	"log/slog"
	"time"

	"github.com/robfig/cron/v3"
	"router-management-service/internal/config"
	"router-management-service/internal/service"
)

// startFailStaleSentScheduler запускает cron-задачу FailStaleSentCommands; при невалидном cron возвращает ошибку.
// Если планировщик выключен или параметры не заданы, возвращает пустой stop без запуска.
func startFailStaleSentScheduler(cfg *config.Config, svc *service.CommandService, log *slog.Logger) (stop func(), err error) {
	stop = func() {}
	if !cfg.Scheduler.Enabled || cfg.Scheduler.SentTimeoutMinutes <= 0 || cfg.Scheduler.Cron == "" {
		return stop, nil
	}
	c := cron.New()
	maxAge := time.Duration(cfg.Scheduler.SentTimeoutMinutes) * time.Minute
	_, err = c.AddFunc(cfg.Scheduler.Cron, func() {
		affected, err := svc.FailStaleSentCommands(context.Background(), maxAge)
		if err != nil {
			log.Error("scheduler: fail stale sent commands", "err", err)
			return
		}
		if affected > 0 {
			log.Info("scheduler: stale sent commands marked failed", "affected", affected)
		}
	})
	if err != nil {
		return stop, err
	}
	c.Start()
	return func() { c.Stop() }, nil
}
