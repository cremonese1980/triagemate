package com.triagemate.triage.routing;

import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionResult;

public interface DecisionRouter {

    void route(DecisionResult result, DecisionContext<?> context);
}
