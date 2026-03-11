package com.triagemate.triage.control.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class AiCostTracker {

    private static final Logger log = LoggerFactory.getLogger(AiCostTracker.class);

    private final AiAdvisoryProperties properties;
    private final AtomicReference<Double> dailyCostUsd = new AtomicReference<>(0.0);
    private final Counter budgetExceededCounter;

    public AiCostTracker(AiAdvisoryProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.budgetExceededCounter = Counter.builder("triagemate.ai.budget.exceeded.total")
                .description("Number of times AI budget was exceeded")
                .register(meterRegistry);
    }

    public void checkBudget(double estimatedCostUsd) {
        if (properties.cost() == null) return;

        if (estimatedCostUsd > properties.cost().maxPerDecisionUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Per-decision cost limit exceeded: " + estimatedCostUsd
                            + " > " + properties.cost().maxPerDecisionUsd());
        }

        double current = dailyCostUsd.get();
        if (current + estimatedCostUsd > properties.cost().maxDailyUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Daily cost limit exceeded: " + (current + estimatedCostUsd)
                            + " > " + properties.cost().maxDailyUsd());
        }
    }

    public void recordCost(double costUsd) {
        dailyCostUsd.updateAndGet(current -> current + costUsd);
    }

    public double getDailyCostUsd() {
        return dailyCostUsd.get();
    }

    public void resetDailyCost() {
        dailyCostUsd.set(0.0);
    }
}
