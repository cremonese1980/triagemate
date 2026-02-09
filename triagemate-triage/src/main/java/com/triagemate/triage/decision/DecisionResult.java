package com.triagemate.triage.decision;

public record DecisionResult(
        DecisionOutcome outcome,
        String reason
) {
}
