package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AiAdviceValidator {

    private final AiAdvisoryProperties properties;

    public AiAdviceValidator(AiAdvisoryProperties properties) {
        this.properties = properties;
    }

    public ValidatedAdvice validate(DecisionResult deterministic, AiDecisionAdvice advice) {
        if (!advice.isPresent()) {
            return ValidatedAdvice.noAdvice();
        }

        double minSuggestion = properties.validation().minConfidenceForSuggestion();
        double minOverride = properties.validation().minConfidenceForOverride();

        if (advice.confidence() < minSuggestion) {
            return ValidatedAdvice.rejected(advice,
                    "Confidence too low: " + advice.confidence());
        }

        Set<String> allowed = properties.allowedClassifications();
        String suggestedClassification = advice.suggestedClassification();
        if (suggestedClassification == null
                || (!allowed.isEmpty() && !allowed.contains(suggestedClassification))) {
            return ValidatedAdvice.rejected(advice,
                    "Invalid classification: " + suggestedClassification);
        }

        if (advice.recommendsOverride() && advice.confidence() >= minOverride) {
            return ValidatedAdvice.accepted(advice);
        }

        // Logged but not applied
        return ValidatedAdvice.advisory(advice);
    }
}
