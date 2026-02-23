package com.triagemate.triage.control.execution;

import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;

public record DecisionExecution(
        DecisionResult result,
        DecisionContext<InputReceivedV1> context
) {}