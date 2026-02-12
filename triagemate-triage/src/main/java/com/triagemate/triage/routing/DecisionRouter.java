package com.triagemate.triage.routing;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;

public interface DecisionRouter {

    void route(DecisionResult result, DecisionContext<?> context);
}
