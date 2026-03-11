package com.triagemate.triage.control.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "triagemate.ai.enabled", havingValue = "true")
public class AiCostResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiCostResetScheduler.class);

    private final AiCostTracker costTracker;

    public AiCostResetScheduler(AiCostTracker costTracker) {
        this.costTracker = costTracker;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyCost() {
        double previousDayCost = costTracker.getDailyCostUsd();
        costTracker.resetDailyCost();
        log.info("Daily AI cost reset. Previous day total: ${}", String.format("%.4f", previousDayCost));
    }
}
