package com.triagemate.triage.control.rag;

import java.time.Instant;

public record DecisionExplanation(
        Long id,
        String decisionId,
        String policyVersion,
        String policyFamily,
        String classification,
        String outcome,
        String decisionReason,
        String decisionContextSummary,
        String contentHash,
        double qualityScore,
        String curatedBy,
        Instant createdAt,
        Instant archivedAt
) {
    public static DecisionExplanation create(
            String decisionId,
            String policyVersion,
            String policyFamily,
            String classification,
            String outcome,
            String decisionReason,
            String decisionContextSummary,
            String contentHash,
            double qualityScore
    ) {
        return new DecisionExplanation(
                null, decisionId, policyVersion, policyFamily,
                classification, outcome, decisionReason, decisionContextSummary,
                contentHash, qualityScore, "system", Instant.now(), null
        );
    }
}
