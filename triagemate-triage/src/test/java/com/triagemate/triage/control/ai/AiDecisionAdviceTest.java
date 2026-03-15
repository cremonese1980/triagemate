package com.triagemate.triage.control.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiDecisionAdviceTest {

    @Test
    void noneSentinel_isNotPresent() {
        assertFalse(AiDecisionAdvice.NONE.isPresent());
    }

    @Test
    void regularAdvice_isPresent() {
        AiDecisionAdvice advice = new AiDecisionAdvice(
                "NORMAL", 0.85, "reason", false,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 100
        );
        assertTrue(advice.isPresent());
    }

    @Test
    void deserializedNoneEquivalent_isNotPresent() {
        // Simulates an advice deserialized from JSON/DB with null classification
        // — must return false even though it's not the NONE singleton
        AiDecisionAdvice deserialized = new AiDecisionAdvice(
                null, 0.0, "AI advisory not available", false,
                null, null, null, null, null, 0, 0, 0.0, 0
        );
        assertFalse(deserialized.isPresent(),
                "isPresent() must be semantic (null classification = not present), not identity-based");
        assertNotSame(AiDecisionAdvice.NONE, deserialized,
                "Deserialized instance is a different object than NONE");
    }
}
