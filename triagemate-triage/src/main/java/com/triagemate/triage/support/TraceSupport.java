package com.triagemate.triage.support;

import com.triagemate.contracts.events.EventEnvelope;

public final class TraceSupport {

    private TraceSupport() {}

    public static String requestId(EventEnvelope<?> envelope) {
        return envelope.trace() != null ? envelope.trace().requestId() : null;
    }

    public static String correlationId(EventEnvelope<?> envelope) {
        return envelope.trace() != null ? envelope.trace().correlationId() : null;
    }
}