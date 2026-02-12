package com.triagemate.triage.control.policy;

import com.triagemate.triage.control.decision.DecisionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptAllPolicyTest {

    @Test
    void evaluateAlwaysReturnsAllowed() {
        AcceptAllPolicy policy = new AcceptAllPolicy();
        DecisionContext<String> context = DecisionContext.of(
                "event-1", "test.event", 1, Instant.EPOCH,
                Map.of(), "payload"
        );

        PolicyResult result = policy.evaluate(context);

        assertTrue(result.allowed());
        assertEquals("accept-all-default-policy", result.reason());
        assertTrue(result.metadata().isEmpty());
    }
}
