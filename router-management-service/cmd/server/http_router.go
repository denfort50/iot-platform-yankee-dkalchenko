package main

import (
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"

	"router-management-service/internal/config"
)

// newHTTPRouter собирает Gin: /metrics, /health, /ready (ready проверяет pool.Ping; при pool == nil вызов /ready приведёт к panic).
func newHTTPRouter(cfg *config.Config, pool *pgxpool.Pool, log *slog.Logger) *gin.Engine {
	if log == nil {
		log = slog.Default()
	}
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	if cfg != nil && cfg.TracingEnabled() {
		r.Use(otelgin.Middleware(cfg.TelemetryServiceName(),
			otelgin.WithFilter(func(r *http.Request) bool {
				p := r.URL.Path
				return p != "/health" && p != "/metrics"
			}),
		))
	}
	r.Use(gin.Recovery())
	r.GET("/metrics", gin.WrapH(promhttp.Handler()))
	r.GET("/health", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "up"}) })
	r.GET("/ready", func(c *gin.Context) {
		if err := pool.Ping(c.Request.Context()); err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "not ready", "error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"status": "ready"})
	})
	return r
}
