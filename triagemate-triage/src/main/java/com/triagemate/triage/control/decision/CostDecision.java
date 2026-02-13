package com.triagemate.triage.control.decision;

public record CostDecision(
        boolean allowed,
        double estimatedCost,
        String reason
) {
    public static CostDecision allow(double estimatedCost, String reason) {
        return new CostDecision(true, estimatedCost, reason);
    }

    public static CostDecision deny(double estimatedCost, String reason) {
        return new CostDecision(false, estimatedCost, reason);
    }
}
