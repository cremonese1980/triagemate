package com.triagemate.triage.control.decision;

public interface DecisionService {
    DecisionResult decide(DecisionContext<?> context);
}
