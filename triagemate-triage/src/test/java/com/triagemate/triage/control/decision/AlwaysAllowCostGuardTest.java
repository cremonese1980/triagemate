package com.triagemate.triage.control.decision;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlwaysAllowCostGuardTest {

    @Test
    void evaluateCostAlwaysAllows() {
        AlwaysAllowCostGuard guard = new AlwaysAllowCostGuard();
        DecisionContext<String> context = DecisionContext.of(
                "event-1", "test.event", 1, Instant.EPOCH, Map.of(), "payload"
        );

        CostDecision decision = guard.evaluateCost(context);

        assertTrue(decision.allowed());
        assertEquals(0.0, decision.estimatedCost());
        assertEquals("cost-guard-disabled", decision.reason());
    }
}
