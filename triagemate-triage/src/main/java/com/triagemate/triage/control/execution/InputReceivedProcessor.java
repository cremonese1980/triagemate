package com.triagemate.triage.control.execution;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionContextFactory;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.observability.DecisionMetrics;
import org.springframework.stereotype.Component;

@Component
public class InputReceivedProcessor {

    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final DecisionMetrics decisionMetrics;

    public InputReceivedProcessor(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            DecisionMetrics decisionMetrics
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.decisionMetrics = decisionMetrics;
    }

    public DecisionExecution process(EventEnvelope<?> envelope) {

        DecisionContext<InputReceivedV1> context = decisionContextFactory.fromEnvelope(envelope);

        DecisionResult result = decisionMetrics.timeDecision(() -> decisionService.decide(context));

        return new DecisionExecution(result, context);
    }
}
