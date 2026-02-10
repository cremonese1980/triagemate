package com.triagemate.triage.decision;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultDecisionService implements DecisionService {

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        return DecisionResult.of(DecisionOutcome.ACCEPT, "default-accept", Map.of());
    }
}
