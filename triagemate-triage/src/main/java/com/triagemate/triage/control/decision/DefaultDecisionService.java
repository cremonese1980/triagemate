package com.triagemate.triage.control.decision;

import java.util.Map;

public class DefaultDecisionService implements DecisionService {

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic-default-accept",
                Map.of("strategy", "rules-v1")
        );
    }
}
