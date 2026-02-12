package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.Policy;
import com.triagemate.triage.control.policy.PolicyResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultDecisionService implements DecisionService {

    private final List<Policy> policies;
    private final CostGuard costGuard;

    public DefaultDecisionService(List<Policy> policies, CostGuard costGuard) {
        this.policies = policies;
        this.costGuard = costGuard;
    }

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        // Step 1: Policy evaluation
        for (Policy policy : policies) {
            PolicyResult policyResult = policy.evaluate(context);
            if (!policyResult.allowed()) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("strategy", "policy-rejection");
                attributes.putAll(policyResult.metadata());
                return DecisionResult.of(
                        DecisionOutcome.REJECT,
                        policyResult.reason(),
                        attributes
                );
            }
        }

        // Step 2: Cost evaluation
        CostDecision costDecision = costGuard.evaluateCost(context);
        if (!costDecision.allowed()) {
            return DecisionResult.of(
                    DecisionOutcome.REJECT,
                    costDecision.reason(),
                    Map.of("strategy", "cost-limit-exceeded",
                           "estimatedCost", String.valueOf(costDecision.estimatedCost()))
            );
        }

        // Step 3: Accept
        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic-default-accept",
                Map.of("strategy", "rules-v1")
        );
    }
}
