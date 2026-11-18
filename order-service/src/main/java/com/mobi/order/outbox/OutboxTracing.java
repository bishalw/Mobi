package com.mobi.order.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Carries a trace across the outbox.
 *
 * <p>The outbox breaks the call chain on purpose: the request commits a row and returns,
 * and a scheduler publishes it seconds later on a different thread. Left alone, the
 * Kafka record would start a brand-new trace and the link between "customer placed an
 * order" and "notification sent the email" would be lost. So the W3C traceparent of the
 * request is stored with the row, and the publisher re-enters that trace before sending;
 * the Kafka producer observation then writes it into the record headers as usual.
 */
@Component
public class OutboxTracing {

    private final Tracer tracer;

    @Autowired
    public OutboxTracing(ObjectProvider<Tracer> tracer) {
        this(tracer.getIfAvailable(() -> Tracer.NOOP));
    }

    private OutboxTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    /** For tests and for running with tracing switched off. */
    public static OutboxTracing noop() {
        return new OutboxTracing(Tracer.NOOP);
    }

    /** The current span as a W3C traceparent header value, or null when not tracing. */
    public String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null || span.isNoop()) {
            return null;
        }
        TraceContext context = span.context();
        return "00-" + context.traceId() + "-" + context.spanId() + "-"
                + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    /** Runs {@code action} inside a child span of the stored traceparent, if there is one. */
    public void runInTrace(String traceparent, String spanName, Runnable action) {
        TraceContext parent = parse(traceparent);
        if (parent == null) {
            action.run();
            return;
        }
        Span span = tracer.spanBuilder().setParent(parent).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            action.run();
        } catch (RuntimeException exp) {
            span.error(exp);
            throw exp;
        } finally {
            span.end();
        }
    }

    private TraceContext parse(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return null;
        }
        return tracer.traceContextBuilder()
                .traceId(parts[1])
                .spanId(parts[2])
                .sampled("01".equals(parts[3]))
                .build();
    }
}
