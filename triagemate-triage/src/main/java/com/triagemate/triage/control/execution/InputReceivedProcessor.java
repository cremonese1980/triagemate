package com.triagemate.triage.control.execution;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionContextFactory;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class InputReceivedProcessor {

    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final MeterRegistry meterRegistry;

    public InputReceivedProcessor(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            MeterRegistry meterRegistry
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.meterRegistry = meterRegistry;
    }

    public DecisionExecution process(EventEnvelope<?> envelope) {

        DecisionContext<InputReceivedV1> context = decisionContextFactory.fromEnvelope(envelope);

        Timer.Sample sample = Timer.start(meterRegistry);
        DecisionResult result = decisionService.decide(context);
        sample.stop(Timer.builder("triagemate.decision.latency")
                .tag("outcome", result.outcome().name())
                .register(meterRegistry));

        return new DecisionExecution(result, context);
    }
}
