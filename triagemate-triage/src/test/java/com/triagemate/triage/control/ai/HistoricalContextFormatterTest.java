package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.rag.DecisionExplanationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalContextFormatterTest {

    private final HistoricalContextFormatter formatter = new HistoricalContextFormatter();

    @Test
    void nullListReturnsEmpty() {
        assertThat(formatter.format(null)).isEmpty();
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(formatter.format(List.of())).isEmpty();
    }

    @Test
    void singleEntryFormattedCorrectly() {
        DecisionExplanationContext ctx = new DecisionExplanationContext(
                1L, "DEVICE_ERROR", "ACCEPT",
                "Telemetry spike detected", "Sensor anomaly",
                "1.0.0", "basic-triage", 0.8, 0.12
        );

        String result = formatter.format(List.of(ctx));

        assertThat(result).contains("SIMILAR HISTORICAL DECISIONS:");
        assertThat(result).contains("1. [Classification: DEVICE_ERROR] Reasoning: Telemetry spike detected");
        assertThat(result).contains("Outcome: ACCEPT");
        assertThat(result).contains("Consider these historical patterns in your analysis.");
    }

    @Test
    void multipleEntriesNumberedCorrectly() {
        DecisionExplanationContext ctx1 = new DecisionExplanationContext(
                1L, "DEVICE_ERROR", "ACCEPT",
                "Telemetry spike detected", null,
                "1.0.0", "basic-triage", 0.8, 0.12
        );
        DecisionExplanationContext ctx2 = new DecisionExplanationContext(
                2L, "URGENT_EMAIL", "REJECT",
                "Low priority noise", null,
                "1.0.0", "basic-triage", 0.7, 0.25
        );

        String result = formatter.format(List.of(ctx1, ctx2));

        assertThat(result).contains("1. [Classification: DEVICE_ERROR]");
        assertThat(result).contains("2. [Classification: URGENT_EMAIL]");
    }

    @Test
    void longReasonTruncatedTo200Chars() {
        String longReason = "A".repeat(300);
        DecisionExplanationContext ctx = new DecisionExplanationContext(
                1L, "RULE_X", "ACCEPT",
                longReason, null,
                "1.0.0", "basic-triage", 0.8, 0.1
        );

        String result = formatter.format(List.of(ctx));

        assertThat(result).contains("A".repeat(200) + "...");
        assertThat(result).doesNotContain("A".repeat(201));
    }

    @Test
    void nullFieldsHandledGracefully() {
        DecisionExplanationContext ctx = new DecisionExplanationContext(
                1L, null, null,
                null, null,
                null, null, 0.0, 0.0
        );

        String result = formatter.format(List.of(ctx));

        assertThat(result).contains("[Classification: UNKNOWN]");
        assertThat(result).contains("Outcome: UNKNOWN");
    }

    @Test
    void totalOutputBoundedByCharLimit() {
        // Create many entries with long reasons to exceed the 6000 char budget
        String reason = "B".repeat(190);
        List<DecisionExplanationContext> contexts = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            contexts.add(new DecisionExplanationContext(
                    i, "RULE_" + i, "ACCEPT",
                    reason, null,
                    "1.0.0", "basic-triage", 0.8, 0.1
            ));
        }

        String result = formatter.format(contexts);

        assertThat(result.length()).isLessThanOrEqualTo(6000);
        assertThat(result).contains("SIMILAR HISTORICAL DECISIONS:");
        assertThat(result).contains("Consider these historical patterns in your analysis.");
    }
}
