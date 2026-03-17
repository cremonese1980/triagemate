package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.Policy;
import com.triagemate.triage.control.policy.PolicyResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultDecisionService implements DecisionService {

    private final List<Policy> policies;
    private final CostGuard costGuard;

    public DefaultDecisionService(List<Policy> policies, CostGuard costGuard) {
        this.policies = policies;
        this.costGuard = costGuard;
    }

    @Override
    public DecisionResult decide(DecisionContext<?> context) {

        String decisionId = UUID.randomUUID().toString();

        // Step 1: Policy evaluation
        for (Policy policy : policies) {
            PolicyResult policyResult = policy.evaluate(context);
            if (!policyResult.allowed()) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("decisionId", decisionId);
                attributes.put("strategy", "policy-rejection");
                attributes.putAll(policyResult.metadata());
                return DecisionResult.of(
                        DecisionOutcome.REJECT,
                        policyResult.reason(),
                        attributes,
                        ReasonCode.POLICY_REJECTED,
                        "Request rejected by policy: " + policyResult.reason()
                );
            }
        }

        // Step 2: Cost evaluation
        CostDecision costDecision = costGuard.evaluateCost(context);
        if (!costDecision.allowed()) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("decisionId", decisionId);
            attributes.put("strategy", "cost-limit-exceeded");
            attributes.put("estimatedCost", String.valueOf(costDecision.estimatedCost()));
            return DecisionResult.of(
                    DecisionOutcome.REJECT,
                    costDecision.reason(),
                    attributes,
                    ReasonCode.COST_LIMIT_EXCEEDED,
                    "Request rejected by cost guard: " + costDecision.reason()
            );
        }

        // Step 3: Accept
        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic-default-accept",
                Map.of("decisionId", decisionId, "strategy", "rules-v1"),
                ReasonCode.ACCEPTED_BY_DEFAULT,
                "All policies passed; accepted by default"
        );
    }
}
