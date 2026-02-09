package com.triagemate.triage.decision;

import java.time.Instant;
import java.util.Map;

public record DecisionContext<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        Map<String, String> trace,
        T payload
) {
}
