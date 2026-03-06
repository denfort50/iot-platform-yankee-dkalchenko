package ru.dkalchenko.failedevents.tracing;

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
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Выполняет действие в спане, продолжая трейс из DLT-сообщения (traceId из payload DeadLetterMessage).
 * Создаётся дочерний спан с новым spanId; родитель — синтетический контекст по переданному traceId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DltTraceContextRunner {

    static String SYNTHETIC_PARENT_SPAN_ID = IdGenerator.random().generateSpanId();
    static String INSTRUMENTATION_SCOPE = "ru.dkalchenko.failedevents";
    static String MDC_TRACE_ID = "traceId";
    static String SPAN_NAME = "process-dlt-minio";

    OpenTelemetry openTelemetry;

    /**
     * Выполняет action в спане. Если traceId не пустой — спан создаётся как дочерний к этому трейсу (с новым spanId).
     */
    public void runInSpan(@Nullable String traceId, Runnable action) {
        var otelTracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);

        io.opentelemetry.api.trace.Span span;
        if (traceId != null && !traceId.isBlank()) {
            SpanContext remoteContext = SpanContext.create(
                    traceId,
                    SYNTHETIC_PARENT_SPAN_ID,
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
            );
            span = otelTracer.spanBuilder(SPAN_NAME)
                    .setParent(Context.current().with(Span.wrap(remoteContext)))
                    .setAttribute("messaging.source", "dlt.v1")
                    .startSpan();
        } else {
            span = otelTracer.spanBuilder(SPAN_NAME)
                    .setAttribute("messaging.source", "dlt.v1")
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
}
