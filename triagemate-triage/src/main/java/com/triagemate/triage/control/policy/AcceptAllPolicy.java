package com.triagemate.triage.control.policy;

import com.triagemate.triage.control.decision.DecisionContext;

import java.util.Map;

public class AcceptAllPolicy implements Policy {

    @Override
    public PolicyResult evaluate(DecisionContext<?> context) {

        return PolicyResult.allow("accept-all-default-policy");
//        return PolicyResult.deny("force-reject-test", Map.of("rule", "test"));
    }
}
