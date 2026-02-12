package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.Policy;
import com.triagemate.triage.control.policy.PolicyResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultDecisionService implements DecisionService {

    private final List<Policy> policies;

    public DefaultDecisionService(List<Policy> policies) {
        this.policies = policies;
    }

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
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

        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic-default-accept",
                Map.of("strategy", "rules-v1")
        );
    }
}
