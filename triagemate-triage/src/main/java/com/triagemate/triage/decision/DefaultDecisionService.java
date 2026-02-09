package com.triagemate.triage.decision;

import org.springframework.stereotype.Component;

@Component
public class DefaultDecisionService implements DecisionService {

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        return new DecisionResult(DecisionOutcome.ACCEPT, "default-accept");
    }
}
