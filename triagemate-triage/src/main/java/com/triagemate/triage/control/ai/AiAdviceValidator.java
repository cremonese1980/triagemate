package com.triagemate.triage.control.ai;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AiAdviceValidator {

    private static final double MIN_CONFIDENCE_FOR_OVERRIDE = 0.85;
    private static final double MIN_CONFIDENCE_FOR_SUGGESTION = 0.70;

    private final AiAdvisoryProperties properties;

    public AiAdviceValidator(AiAdvisoryProperties properties) {
        this.properties = properties;
    }

    public ValidatedAdvice validate(AiDecisionAdvice advice) {
        if (!advice.isPresent()) {
            return ValidatedAdvice.noAdvice();
        }

        if (advice.confidence() < MIN_CONFIDENCE_FOR_SUGGESTION) {
            return ValidatedAdvice.rejected(advice,
                    "Confidence too low: " + advice.confidence());
        }

        Set<String> allowed = properties.allowedClassifications();
        if (!allowed.isEmpty()
                && advice.suggestedClassification() != null
                && !allowed.contains(advice.suggestedClassification())) {
            return ValidatedAdvice.rejected(advice,
                    "Invalid classification: " + advice.suggestedClassification());
        }

        if (advice.recommendsOverride() && advice.confidence() >= MIN_CONFIDENCE_FOR_OVERRIDE) {
            return ValidatedAdvice.accepted(advice);
        }

        // Logged but not applied
        return ValidatedAdvice.advisory(advice);
    }
}
