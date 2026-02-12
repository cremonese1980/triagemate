package com.triagemate.triage.control.policy;

import com.triagemate.triage.control.decision.DecisionContext;

public interface Policy {
    PolicyResult evaluate(DecisionContext<?> context);
}
