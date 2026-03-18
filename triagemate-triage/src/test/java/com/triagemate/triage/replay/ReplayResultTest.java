package com.triagemate.triage.replay;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayResultTest {

    private static final UUID DECISION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void noDrift_whenOutcomesMatch() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of("strategy", "rules-v1"),
                "ACCEPT", "1.0.0", Map.of("strategy", "rules-v1")
        );

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(result.newOutcome()).isEqualTo("ACCEPT");
        assertThat(result.attributeDifferences()).isEmpty();
    }

    @Test
    void driftDetected_whenOutcomesDiffer() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of("strategy", "rules-v1"),
                "REJECT", "2.0.0", Map.of("strategy", "policy-rejection")
        );

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(result.newOutcome()).isEqualTo("REJECT");
    }

    @Test
    void detectsAttributeDifferences_changed() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of("strategy", "rules-v1"),
                "ACCEPT", "2.0.0", Map.of("strategy", "rules-v2")
        );

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.attributeDifferences()).contains("changed: strategy [rules-v1 -> rules-v2]");
    }

    @Test
    void detectsAttributeDifferences_addedAndRemoved() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of("a", "1"),
                "ACCEPT", "1.0.0", Map.of("b", "2")
        );

        assertThat(result.attributeDifferences()).contains("removed: a");
        assertThat(result.attributeDifferences()).contains("added: b");
    }

    @Test
    void nullAttributes_treatedAsEmpty() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", null,
                "ACCEPT", "1.0.0", null
        );

        assertThat(result.originalAttributes()).isEmpty();
        assertThat(result.newAttributes()).isEmpty();
        assertThat(result.attributeDifferences()).isEmpty();
    }

    @Test
    void preservesDecisionId() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of(),
                "ACCEPT", "1.0.0", Map.of()
        );

        assertThat(result.originalDecisionId()).isEqualTo(DECISION_ID);
    }

    @Test
    void preservesPolicyVersions() {
        ReplayResult result = ReplayResult.compare(
                DECISION_ID,
                "ACCEPT", "1.0.0", Map.of(),
                "ACCEPT", "2.0.0", Map.of()
        );

        assertThat(result.originalPolicyVersion()).isEqualTo("1.0.0");
        assertThat(result.newPolicyVersion()).isEqualTo("2.0.0");
    }
}
