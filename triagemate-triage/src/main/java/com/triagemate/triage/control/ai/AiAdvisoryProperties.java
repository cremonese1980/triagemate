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
        Cost cost
) {
    public AiAdvisoryProperties {

        provider = Objects.requireNonNullElse(provider, "anthropic");
        allowedClassifications = Objects.requireNonNullElse(allowedClassifications, Set.of());
        timeouts = Objects.requireNonNullElse(timeouts, new Timeouts(Duration.ofSeconds(5)));
        cost = Objects.requireNonNullElse(cost, new Cost(0.05, 100.00));
    }

    public record Timeouts(Duration advisory) {
        public Timeouts {
            if (advisory == null) advisory = Duration.ofSeconds(5);
        }
    }

    public record Cost(double maxPerDecisionUsd, double maxDailyUsd) {
    }
}
