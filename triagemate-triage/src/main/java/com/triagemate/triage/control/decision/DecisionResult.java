package com.triagemate.triage.control.decision;

import java.util.Map;

public record DecisionResult(
        DecisionOutcome outcome,
        String reason,
        Map<String, Object> attributes,
        ReasonCode reasonCode,
        String humanReadableReason
) {
    public DecisionResult {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Backward-compatible factory: no reasonCode or humanReadableReason. */
    public static DecisionResult of(DecisionOutcome outcome, String reason, Map<String, Object> attributes) {
        return new DecisionResult(outcome, reason, attributes, null, null);
    }

    /** Full factory with explainability fields. */
    public static DecisionResult of(
            DecisionOutcome outcome, String reason, Map<String, Object> attributes,
            ReasonCode reasonCode, String humanReadableReason
    ) {
        return new DecisionResult(outcome, reason, attributes, reasonCode, humanReadableReason);
    }
}
