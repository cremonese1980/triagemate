package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;

public interface DecisionOutcomePublisher {

    void publish(DecisionResult result, DecisionContext<?> context);
}
