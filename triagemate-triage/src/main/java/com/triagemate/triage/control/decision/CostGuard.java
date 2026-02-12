package com.triagemate.triage.control.decision;

public interface CostGuard {
    CostDecision evaluateCost(DecisionContext<?> context);
}
