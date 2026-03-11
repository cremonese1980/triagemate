package com.triagemate.triage.control.ai;

/**
 * Result of deterministic validation of AI advice.
 */
public record ValidatedAdvice(
        Status status,
        AiDecisionAdvice advice,
        String rejectionReason
) {
    public enum Status {
        ACCEPTED,
        ADVISORY,
        REJECTED,
        NO_ADVICE
    }

    public static ValidatedAdvice accepted(AiDecisionAdvice advice) {
        return new ValidatedAdvice(Status.ACCEPTED, advice, null);
    }

    public static ValidatedAdvice advisory(AiDecisionAdvice advice) {
        return new ValidatedAdvice(Status.ADVISORY, advice, null);
    }

    public static ValidatedAdvice rejected(AiDecisionAdvice advice, String reason) {
        return new ValidatedAdvice(Status.REJECTED, advice, reason);
    }

    public static ValidatedAdvice noAdvice() {
        return new ValidatedAdvice(Status.NO_ADVICE, AiDecisionAdvice.NONE, null);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }
}
