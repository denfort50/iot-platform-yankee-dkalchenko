package ru.dkalchenko.collector.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.experimental.UtilityClass;
import org.springframework.lang.Nullable;

/**
 * Поддержка сквозного трейсинга: захват контекста при записи в outbox
 * и восстановление при публикации в devices.v1.
 */
@UtilityClass
public class TraceContextCapture {

    private static final String INSTRUMENTATION_SCOPE = "ru.dkalchenko.collector";

    /**
     * Возвращает trace_id текущего спана или null.
     */
    @Nullable
    public static String getCurrentTraceId(Tracer tracer) {
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    /**
     * Возвращает span_id текущего спана или null.
     */
    @Nullable
    public static String getCurrentSpanId(Tracer tracer) {
        Span span = tracer.currentSpan();
        return span != null ? span.context().spanId() : null;
    }

    /**
     * Выполняет действие в контексте продолжённого трейса (родитель = сохранённый trace_id/span_id).
     * Использует переданный OpenTelemetry (бин Spring), чтобы спан экспортировался в тот же Tempo, что и остальные.
     * Если traceId или spanId пустые, выполняется без смены контекста и без создания спана.
     *
     * @param openTelemetry тот же OpenTelemetry, что настроен в приложении (Micrometer bridge)
     * @param traceId       trace_id из outbox
     * @param spanId        span_id из outbox
     * @param spanName      имя спана (например, "publish-device-kafka")
     * @param deviceId      device_id для атрибутов
     * @param action        действие (публикация в Kafka и т.д.)
     */
    public static void runInContinuedTrace(
            OpenTelemetry openTelemetry,
            @Nullable String traceId,
            @Nullable String spanId,
            String spanName,
            String deviceId,
            Runnable action) {
        if (traceId == null || traceId.isBlank() || spanId == null || spanId.isBlank()) {
            action.run();
            return;
        }
        SpanContext remoteContext = SpanContext.create(
                traceId,
                spanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        io.opentelemetry.api.trace.Span span = otelTracer.spanBuilder(spanName)
                .setParent(Context.current().with(io.opentelemetry.api.trace.Span.wrap(remoteContext)))
                .setAttribute("device.id", deviceId)
                .setAttribute("messaging.destination", "devices.v1")
                .startSpan();
        try (Scope _ = span.makeCurrent()) {
            action.run();
        } finally {
            span.end();
        }
    }
}
