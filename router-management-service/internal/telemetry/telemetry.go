// Package telemetry — инициализация OpenTelemetry: OTLP HTTP → Alloy/Tempo, пропагация контекста.
package telemetry

import (
	"context"
	"fmt"
	"strings"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Setup подключает экспорт трейсов по OTLP/HTTP (полный URL, например http://alloy:4318/v1/traces).
// При пустом endpoint возвращает no-op shutdown без изменения глобального TracerProvider.
func Setup(ctx context.Context, endpointURL, serviceName string) (func(context.Context) error, error) {
	endpointURL = strings.TrimSpace(endpointURL)
	if endpointURL == "" {
		return func(context.Context) error { return nil }, nil
	}
	serviceName = strings.TrimSpace(serviceName)
	if serviceName == "" {
		serviceName = "router-management-service"
	}

	exp, err := otlptracehttp.New(ctx, otlptracehttp.WithEndpointURL(endpointURL))
	if err != nil {
		return nil, fmt.Errorf("otlp http trace exporter: %w", err)
	}

	// Явный service.name из конфига; OTEL_RESOURCE_ATTRIBUTES можно добавить при необходимости отдельным детектором.
	res, err := resource.New(ctx,
		resource.WithAttributes(semconv.ServiceName(serviceName)),
	)
	if err != nil {
		return nil, fmt.Errorf("otel resource: %w", err)
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))
	return tp.Shutdown, nil
}
