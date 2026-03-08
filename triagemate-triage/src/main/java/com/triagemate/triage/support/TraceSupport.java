package com.triagemate.triage.support;

import com.triagemate.contracts.events.EventEnvelope;

import java.util.Objects;
import java.util.UUID;

public final class TraceSupport {

    private TraceSupport() {}

    public static String requestId(EventEnvelope<?> envelope) {
        return envelope.trace() != null ? envelope.trace().requestId() : null;
    }

    public static String correlationId(EventEnvelope<?> envelope) {
        return envelope.trace() != null ? envelope.trace().correlationId() : null;
    }

    /**
     * Returns the requestId from the envelope trace, or generates a fallback UUID
     * prefixed with "generated-" if the trace or requestId is null.
     */
    public static String requestIdOrGenerate(EventEnvelope<?> envelope) {
        String id = requestId(envelope);
        return id != null ? id : "generated-" + UUID.randomUUID();
    }

    /**
     * Returns the correlationId from the envelope trace, or generates a fallback UUID
     * prefixed with "generated-" if the trace or correlationId is null.
     */
    public static String correlationIdOrGenerate(EventEnvelope<?> envelope) {
        String id = correlationId(envelope);
        return id != null ? id : "generated-" + UUID.randomUUID();
    }

    /**
     * Returns the eventId, or "unknown" if null.
     */
    public static String eventIdOrDefault(EventEnvelope<?> envelope) {
        return Objects.requireNonNullElse(envelope.eventId(), "unknown");
    }
}
