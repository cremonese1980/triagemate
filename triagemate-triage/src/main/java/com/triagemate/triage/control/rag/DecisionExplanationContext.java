package com.triagemate.triage.control.rag;

public record DecisionExplanationContext(
        long explanationId,
        String classification,
        String outcome,
        String decisionReason,
        String decisionContextSummary,
        String policyVersion,
        String policyFamily,
        double qualityScore,
        double similarityScore
) {
}
