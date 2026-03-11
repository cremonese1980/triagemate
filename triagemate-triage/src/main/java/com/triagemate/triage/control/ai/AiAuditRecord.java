package com.triagemate.triage.control.ai;

public record AiAuditRecord(
        String decisionId,
        String eventId,
        String provider,
        String model,
        String modelVersion,
        String promptVersion,
        String promptHash,
        Double confidence,
        String suggestedClassification,
        Boolean recommendsOverride,
        String reasoning,
        Boolean acceptedByValidator,
        String rejectionReason,
        Integer inputTokens,
        Integer outputTokens,
        Double costUsd,
        Long latencyMs,
        String errorType,
        String errorMessage
) {
    public static AiAuditRecord fromAdvice(
            String decisionId,
            String eventId,
            AiDecisionAdvice advice,
            ValidatedAdvice validated
    ) {
        return new AiAuditRecord(
                decisionId,
                eventId,
                advice.provider(),
                advice.model(),
                advice.modelVersion(),
                advice.promptVersion(),
                advice.promptHash(),
                advice.confidence(),
                advice.suggestedClassification(),
                advice.recommendsOverride(),
                advice.reasoning(),
                validated.isAccepted(),
                validated.rejectionReason(),
                advice.inputTokens(),
                advice.outputTokens(),
                advice.costUsd(),
                advice.latencyMs(),
                null,
                null
        );
    }

    public static AiAuditRecord fromError(
            String decisionId,
            String eventId,
            String errorType,
            String errorMessage
    ) {
        return new AiAuditRecord(
                decisionId,
                eventId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                errorType,
                errorMessage
        );
    }
}
