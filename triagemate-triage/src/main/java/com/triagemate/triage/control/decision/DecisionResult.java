package com.triagemate.triage.control.decision;

import java.util.Map;

public record DecisionResult(
        DecisionOutcome outcome,
        String reason,
        Map<String, Object> attributes
) {
    public DecisionResult {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static DecisionResult of(DecisionOutcome outcome, String reason, Map<String, Object> attributes) {
        return new DecisionResult(outcome, reason, attributes);
    }
}
