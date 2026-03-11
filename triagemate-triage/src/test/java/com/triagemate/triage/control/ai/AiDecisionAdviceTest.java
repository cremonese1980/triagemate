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
}
