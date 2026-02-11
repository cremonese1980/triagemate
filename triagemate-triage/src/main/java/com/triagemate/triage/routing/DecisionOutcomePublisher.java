package com.triagemate.triage.routing;

import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionResult;

public interface DecisionOutcomePublisher {

    void publish(DecisionResult result, DecisionContext<?> context);
}
