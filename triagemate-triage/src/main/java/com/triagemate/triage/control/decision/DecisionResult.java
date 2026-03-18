package com.triagemate.triage.control.decision;

import java.util.Map;

public record DecisionResult(
        DecisionOutcome outcome,
        String reason,
        Map<String, Object> attributes,
        ReasonCode reasonCode,
        String humanReadableReason,
        String policyVersion
) {
    public DecisionResult {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Backward-compatible factory: no reasonCode, humanReadableReason, or policyVersion. */
    public static DecisionResult of(DecisionOutcome outcome, String reason, Map<String, Object> attributes) {
        return new DecisionResult(outcome, reason, attributes, null, null, null);
    }

    /** Factory with explainability fields but no policyVersion. */
    public static DecisionResult of(
            DecisionOutcome outcome, String reason, Map<String, Object> attributes,
            ReasonCode reasonCode, String humanReadableReason
    ) {
        return new DecisionResult(outcome, reason, attributes, reasonCode, humanReadableReason, null);
    }

    /** Full factory with explainability fields and policyVersion. */
    public static DecisionResult of(
            DecisionOutcome outcome, String reason, Map<String, Object> attributes,
            ReasonCode reasonCode, String humanReadableReason, String policyVersion
    ) {
        return new DecisionResult(outcome, reason, attributes, reasonCode, humanReadableReason, policyVersion);
    }

    /** Returns a copy of this result with the given policyVersion. */
    public DecisionResult withPolicyVersion(String policyVersion) {
        return new DecisionResult(outcome, reason, attributes, reasonCode, humanReadableReason, policyVersion);
    }
}
