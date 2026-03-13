package com.triagemate.triage.control.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAdvisoryPropertiesTest {

    @Test
    void validation_rejectsOverrideLowerThanSuggestion() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiAdvisoryProperties.Validation(0.85, 0.70) // override < suggestion
        );
    }

    @Test
    void validation_rejectsSuggestionBelowZero() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiAdvisoryProperties.Validation(-0.01, 0.85)
        );
    }

    @Test
    void validation_rejectsSuggestionAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiAdvisoryProperties.Validation(1.01, 1.05)
        );
    }

    @Test
    void validation_rejectsOverrideBelowZero() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiAdvisoryProperties.Validation(0.0, -0.01)
        );
    }

    @Test
    void validation_rejectsOverrideAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiAdvisoryProperties.Validation(0.70, 1.01)
        );
    }

    @Test
    void validation_acceptsValidConfiguration() {
        assertDoesNotThrow(() ->
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
    }

    @Test
    void validation_acceptsBoundaryValues() {
        assertDoesNotThrow(() ->
                new AiAdvisoryProperties.Validation(0.0, 0.0)
        );
        assertDoesNotThrow(() ->
                new AiAdvisoryProperties.Validation(1.0, 1.0)
        );
        assertDoesNotThrow(() ->
                new AiAdvisoryProperties.Validation(0.0, 1.0)
        );
    }

    @Test
    void defaults_areAppliedWhenNullProvided() {
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                false, null, null, null, null, null
        );

        assert props.provider().equals("anthropic");
        assert props.allowedClassifications().isEmpty();
        assert props.timeouts().advisory().equals(Duration.ofSeconds(5));
        assert props.cost().maxPerDecisionUsd() == 0.05;
        assert props.cost().maxDailyUsd() == 100.00;
        assert props.validation().minConfidenceForSuggestion() == 0.70;
        assert props.validation().minConfidenceForOverride() == 0.85;
    }
}
