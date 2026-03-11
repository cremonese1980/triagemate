package com.triagemate.triage.control.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
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
        if (enabled == false) return; // skip validation when disabled
        if (provider == null) provider = "anthropic";
        if (allowedClassifications == null) allowedClassifications = Set.of();
        if (timeouts == null) timeouts = new Timeouts(Duration.ofSeconds(5));
        if (cost == null) cost = new Cost(0.05, 100.00);
    }

    public record Timeouts(Duration advisory) {
        public Timeouts {
            if (advisory == null) advisory = Duration.ofSeconds(5);
        }
    }

    public record Cost(double maxPerDecisionUsd, double maxDailyUsd) {
    }
}
