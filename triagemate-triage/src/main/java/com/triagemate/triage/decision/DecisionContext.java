package com.triagemate.triage.decision;

import java.time.Instant;

public record DecisionContext<T>(
        String eventId,
        String eventType,
        Instant occurredAt,
        Trace trace,
        T payload
) {
    public record Trace(
            String requestId,
            String correlationId
    ) {
    }
}
