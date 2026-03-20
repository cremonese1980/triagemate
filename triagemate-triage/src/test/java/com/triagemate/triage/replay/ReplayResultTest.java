package com.triagemate.triage.replay;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayResultTest {

    @Test
    void compare_detectsDriftWhenPolicyVersionChanges() {
        ReplayResult result = ReplayResult.compare(
                UUID.randomUUID(),
                "ACCEPT", "1.0.0", Map.of("decisionId", "old", "strategy", "rules-v1"),
                "ACCEPT", "2.0.0", Map.of("decisionId", "new", "strategy", "rules-v1")
        );

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.attributeDifferences()).isEmpty();
    }

    @Test
    void compare_ignoresDecisionIdDifferences() {
        ReplayResult result = ReplayResult.compare(
                UUID.randomUUID(),
                "ACCEPT", "1.0.0", Map.of("decisionId", "old", "strategy", "ai-override"),
                "ACCEPT", "1.0.0", Map.of("decisionId", "new", "strategy", "ai-override")
        );

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.attributeDifferences()).isEmpty();
    }


    @Test
    void compare_ignoresStrategyAndAiOnlyDifferences() {
        ReplayResult result = ReplayResult.compare(
                UUID.randomUUID(),
                "ACCEPT", "1.0.0",
                Map.of("decisionId", "old", "strategy", "ai-override", "aiAdviceStatus", "ACCEPTED"),
                "ACCEPT", "1.0.0",
                Map.of("decisionId", "new", "strategy", "rules-v1", "aiAdviceStatus", "SKIPPED")
        );

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.attributeDifferences()).hasSize(2);
    }

    @Test
    void compare_detectsDriftWhenMeaningfulAttributesChange() {
        ReplayResult result = ReplayResult.compare(
                UUID.randomUUID(),
                "ACCEPT", "1.0.0", Map.of("decisionId", "old", "estimatedCost", "10.0"),
                "ACCEPT", "1.0.0", Map.of("decisionId", "new", "estimatedCost", "250.0")
        );

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.attributeDifferences())
                .containsExactly("changed: estimatedCost [10.0 -> 250.0]");
    }

}
