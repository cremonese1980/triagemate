package com.triagemate.triage.control.decision;

public class AlwaysAllowCostGuard implements CostGuard {

    @Override
    public CostDecision evaluateCost(DecisionContext<?> context) {
        return CostDecision.allow(0.0, "cost-guard-disabled");
    }
}
