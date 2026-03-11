package com.triagemate.triage.control.ai;

/**
 * Structured advisory output from the AI provider.
 * Immutable record — every field is explicit, typed, and serializable.
 */
public record AiDecisionAdvice(
        String suggestedClassification,
        double confidence,
        String reasoning,
        boolean recommendsOverride,
        String provider,
        String model,
        String modelVersion,
        String promptVersion,
        String promptHash,
        int inputTokens,
        int outputTokens,
        double costUsd,
        long latencyMs
) {
    public static final AiDecisionAdvice NONE = new AiDecisionAdvice(
            null, 0.0, "AI advisory not available", false,
            null, null, null, null, null, 0, 0, 0.0, 0
    );

    public boolean isPresent() {
        return this != NONE;
    }
}
