package com.triagemate.triage.control.decision;

import java.time.Instant;
import java.util.Map;

public record DecisionContext<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        Map<String, String> trace,
        T payload,
        ActorContext actorContext
) {
    /** Backward-compatible factory without ActorContext. */
    public static <T> DecisionContext<T> of(
            String eventId, String eventType, int eventVersion,
            Instant occurredAt, Map<String, String> trace, T payload
    ) {
        return new DecisionContext<>(eventId, eventType, eventVersion,
                occurredAt, trace, payload, null);
    }
}
