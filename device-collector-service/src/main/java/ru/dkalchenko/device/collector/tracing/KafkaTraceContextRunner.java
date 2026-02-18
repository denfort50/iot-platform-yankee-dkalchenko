package ru.dkalchenko.device.collector.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.IdGenerator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Выполняет действие в спане с trace_id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KafkaTraceContextRunner {

    private static final String SYNTHETIC_PARENT_SPAN_ID = IdGenerator.random().generateSpanId();
    private static final String INSTRUMENTATION_SCOPE = "ru.dkalchenko.device.collector";
    private static final String MDC_TRACE_ID = "traceId";

    OpenTelemetry openTelemetry;

    /**
     * Парсит trace_id, создаёт спан, выполняет action.
     */
    public void runInSpan(@Nullable Headers headers, String deviceId, String spanName, Runnable action) {
        String traceId = parseTraceId(headers);
        log.info("TraceId: {}", traceId);
        var otelTracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);

        io.opentelemetry.api.trace.Span span;
        if (traceId != null) {
            SpanContext remoteContext = SpanContext.create(
                    traceId,
                    SYNTHETIC_PARENT_SPAN_ID,
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
            );
            span = otelTracer.spanBuilder(spanName)
                    .setParent(Context.current().with(Span.wrap(remoteContext)))
                    .setAttribute("device.id", deviceId != null ? deviceId : "")
                    .setAttribute("messaging.source", "devices.v1")
                    .setAttribute("db.operation", "upsert")
                    .startSpan();
        } else {
            span = otelTracer.spanBuilder(spanName)
                    .setAttribute("device.id", deviceId != null ? deviceId : "")
                    .setAttribute("messaging.source", "devices.v1")
                    .setAttribute("db.operation", "upsert")
                    .startSpan();
        }

        try (Scope _ = span.makeCurrent()) {
            MDC.put(MDC_TRACE_ID, span.getSpanContext().getTraceId());
            try {
                action.run();
            } finally {
                MDC.remove(MDC_TRACE_ID);
            }
        } finally {
            span.end();
        }
    }

    @Nullable
    private static String parseTraceId(@Nullable Headers headers) {
        if (headers == null) return null;
        Header traceId = headers.lastHeader("trace_id");
        if (traceId == null) return null;
        return new String(traceId.value());
    }

}
