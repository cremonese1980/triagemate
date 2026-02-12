package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.decision.DecisionOutcome;

import java.time.Instant;
import java.util.Map;

public record DecisionLogEntry(
        String eventId,
        String eventType,
        int eventVersion,
        Instant timestamp,
        DecisionOutcome outcome,
        String reason,
        Map<String, Object> policyMetadata,
        Map<String, Object> costMetadata
) {
    public static DecisionLogEntry from(
            String eventId, String eventType, int eventVersion,
            DecisionOutcome outcome, String reason,
            Map<String, Object> attributes
    ) {
        return new DecisionLogEntry(
                eventId, eventType, eventVersion,
                Instant.now(),
                outcome, reason,
                attributes,
                Map.of()
        );
    }
}
