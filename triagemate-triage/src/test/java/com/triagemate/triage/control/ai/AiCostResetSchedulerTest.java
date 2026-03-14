package com.triagemate.triage.control.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiCostResetSchedulerTest {

    @Test
    void resetDailyCost_clearsDailyAccumulation() {
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test", Set.of(),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        AiCostTracker tracker = new AiCostTracker(props, new SimpleMeterRegistry());

        tracker.recordCost(12.50);
        tracker.recordCost(7.30);
        assertEquals(19.80, tracker.getDailyCostUsd(), 0.001);

        AiCostResetScheduler scheduler = new AiCostResetScheduler(tracker);
        scheduler.resetDailyCost();

        assertEquals(0.0, tracker.getDailyCostUsd(), 0.001);
    }

    @Test
    void resetDailyCost_allowsBudgetChecksAfterReset() {
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test", Set.of(),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 10.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        AiCostTracker tracker = new AiCostTracker(props, new SimpleMeterRegistry());

        // Exhaust budget
        tracker.recordCost(9.99);
        assertThrows(BudgetExceededException.class, () -> tracker.checkBudget(0.05));

        // Reset and verify budget is available again
        AiCostResetScheduler scheduler = new AiCostResetScheduler(tracker);
        scheduler.resetDailyCost();

        assertDoesNotThrow(() -> tracker.checkBudget(0.05));
    }
}
