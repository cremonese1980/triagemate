package com.triagemate.triage.control.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "triagemate.ai")
public record AiAdvisoryProperties(
        boolean enabled,
        String provider,
        Set<String> allowedClassifications,
        Timeouts timeouts,
        Cost cost,
        Validation validation
) {
    public AiAdvisoryProperties {

        provider = Objects.requireNonNullElse(provider, "anthropic");
        allowedClassifications = Objects.requireNonNullElse(allowedClassifications, Set.of());
        timeouts = Objects.requireNonNullElse(timeouts, new Timeouts(Duration.ofSeconds(5)));
        cost = Objects.requireNonNullElse(cost, new Cost(0.05, 100.00));
        validation = Objects.requireNonNullElse(validation, new Validation(0.70, 0.85));
    }

    public AiAdvisoryProperties(
            boolean enabled,
            String provider,
            Set<String> allowedClassifications,
            Timeouts timeouts,
            Cost cost
    ) {
        this(enabled, provider, allowedClassifications, timeouts, cost, new Validation(0.70, 0.85));
    }

    public record Timeouts(Duration advisory) {
        public Timeouts {
            if (advisory == null) advisory = Duration.ofSeconds(5);
        }
    }

    public record Cost(double maxPerDecisionUsd, double maxDailyUsd) {
    }

    public record Validation(double minConfidenceForSuggestion, double minConfidenceForOverride) {
        public Validation {
            if (minConfidenceForSuggestion < 0.0 || minConfidenceForSuggestion > 1.0) {
                throw new IllegalArgumentException("minConfidenceForSuggestion must be in [0,1]");
            }
            if (minConfidenceForOverride < 0.0 || minConfidenceForOverride > 1.0) {
                throw new IllegalArgumentException("minConfidenceForOverride must be in [0,1]");
            }
            if (minConfidenceForOverride < minConfidenceForSuggestion) {
                throw new IllegalArgumentException("minConfidenceForOverride must be >= minConfidenceForSuggestion");
            }
        }
    }
}
