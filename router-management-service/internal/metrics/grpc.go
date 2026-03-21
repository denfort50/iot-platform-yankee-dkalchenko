// Package metrics — Prometheus-метрики gRPC API (счётчики запросов и гистограмма длительности).
package metrics

import (
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

const (
	// StatusSuccess — запрос обработан успешно (без ошибки в теле ответа там, где ошибка передаётся в message).
	StatusSuccess = "success"
	// StatusFailure — ошибка gRPC и/или бизнес-ошибка в ответе.
	StatusFailure = "failure"
)

var (
	grpcRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "router_management_grpc_requests_total",
			Help: "Total gRPC unary requests by RPC name and outcome (success or failure).",
		},
		[]string{"rpc", "status"},
	)
	grpcRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "router_management_grpc_request_duration_seconds",
			Help:    "Latency of gRPC unary handlers in seconds.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"rpc"},
	)
)

// RecordGRPC фиксирует итог вызова RPC: успех/неуспех и длительность.
func RecordGRPC(rpc string, success bool, elapsed time.Duration) {
	st := StatusFailure
	if success {
		st = StatusSuccess
	}
	grpcRequestsTotal.WithLabelValues(rpc, st).Inc()
	grpcRequestDuration.WithLabelValues(rpc).Observe(elapsed.Seconds())
}
