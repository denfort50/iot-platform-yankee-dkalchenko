// Package main — точка входа сервиса управления роутерами.
// Запускает gRPC-сервер API, HTTP-сервер для health/ready, подключается к PostgreSQL и выполняет миграции.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/joho/godotenv"
)

// main загружает .env, поднимает приложение через initApp и ждёт SIGINT/SIGTERM.
func main() {
	_ = godotenv.Load()
	log := slog.Default()
	ctx := context.Background()

	rt, err := initApp(ctx, log)
	if err != nil {
		log.Error("init app", "err", err)
		os.Exit(1)
	}

	defer rt.shutdownTelemetry()
	defer rt.pool.Close()
	defer rt.stopSched()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info("shutting down")
	rt.grpcServer.GracefulStop()
	_ = rt.httpSrv.Shutdown(context.Background())
}
