package com.triagemate.triage.control.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    void logBudgetConfig() {
        if (properties.cost() != null) {
            log.info("AI cost tracker initialized [props@{}]: maxPerDecision={} USD, maxDaily={} USD, estimate={} USD",
                    Integer.toHexString(System.identityHashCode(properties)),
                    properties.cost().maxPerDecisionUsd(), properties.cost().maxDailyUsd(),
                    properties.cost().estimatedCostUsd());
        }
    }

    /**
     * Atomically checks budget limits and reserves the estimated cost.
     * This prevents TOCTOU race conditions where concurrent threads could
     * each pass the check and then collectively exceed the budget.
     */
    public synchronized void checkAndReserveBudget(double estimatedCostUsd) {
        if (properties.cost() == null) return;

        double current = dailyCostUsd.get();
        log.info("Budget check: daily={}, estimate={}, maxPerDecision={}, maxDaily={}",
                current, estimatedCostUsd,
                properties.cost().maxPerDecisionUsd(), properties.cost().maxDailyUsd());

        if (estimatedCostUsd > properties.cost().maxPerDecisionUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Per-decision cost limit exceeded: " + estimatedCostUsd
                            + " > " + properties.cost().maxPerDecisionUsd());
        }

        if (current + estimatedCostUsd > properties.cost().maxDailyUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Daily cost limit exceeded: " + (current + estimatedCostUsd)
                            + " > " + properties.cost().maxDailyUsd());
        }

        // Reserve the cost atomically within the same lock
        double newTotal = dailyCostUsd.updateAndGet(c -> c + estimatedCostUsd);
        log.info("Budget reserved: {} USD, daily total now: {} USD", estimatedCostUsd, newTotal);
    }

    /**
     * Records the actual cost difference after an AI call completes.
     * If actual cost differs from the estimate, adjusts the running total.
     */
    public synchronized void recordActualCost(double estimatedCostUsd, double actualCostUsd) {
        double adjustment = actualCostUsd - estimatedCostUsd;
        if (Math.abs(adjustment) > 1e-9) {
            double newTotal = dailyCostUsd.updateAndGet(current -> current + adjustment);
            log.info("Cost adjusted: estimated={} USD, actual={} USD, adjustment={} USD, daily total now: {} USD",
                    estimatedCostUsd, actualCostUsd, adjustment, newTotal);
        }
    }

    /**
     * @deprecated Use {@link #checkAndReserveBudget(double)} for thread-safe budget enforcement.
     */
    @Deprecated
    public synchronized void checkBudget(double estimatedCostUsd) {
        if (properties.cost() == null) return;

        double current = dailyCostUsd.get();
        log.info("Budget check: daily={}, estimate={}, maxPerDecision={}, maxDaily={}",
                current, estimatedCostUsd,
                properties.cost().maxPerDecisionUsd(), properties.cost().maxDailyUsd());

        if (estimatedCostUsd > properties.cost().maxPerDecisionUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Per-decision cost limit exceeded: " + estimatedCostUsd
                            + " > " + properties.cost().maxPerDecisionUsd());
        }

        if (current + estimatedCostUsd > properties.cost().maxDailyUsd()) {
            budgetExceededCounter.increment();
            throw new BudgetExceededException(
                    "Daily cost limit exceeded: " + (current + estimatedCostUsd)
                            + " > " + properties.cost().maxDailyUsd());
        }
    }

    /**
     * @deprecated Use {@link #recordActualCost(double, double)} to adjust after reservation.
     */
    @Deprecated
    public synchronized void recordCost(double costUsd) {
        double newTotal = dailyCostUsd.updateAndGet(current -> current + costUsd);
        log.info("Cost recorded: {} USD, daily total now: {} USD", costUsd, newTotal);
    }

    public double getDailyCostUsd() {
        return dailyCostUsd.get();
    }

    public synchronized void resetDailyCost() {
        dailyCostUsd.set(0.0);
    }
}
