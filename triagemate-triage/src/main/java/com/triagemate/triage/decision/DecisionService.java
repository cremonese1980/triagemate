package com.triagemate.triage.decision;

public interface DecisionService {
    DecisionResult decide(DecisionContext<?> context);
}
