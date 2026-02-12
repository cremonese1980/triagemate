package com.triagemate.triage.control.policy;

import com.triagemate.triage.control.decision.DecisionContext;

public class AcceptAllPolicy implements Policy {

    @Override
    public PolicyResult evaluate(DecisionContext<?> context) {
        return PolicyResult.allow("accept-all-default-policy");
    }
}
