package com.triagemate.triage.control.execution;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionContextFactory;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.idempotency.EventIdIdempotencyGuard;
import com.triagemate.triage.support.TraceSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
public class InputReceivedProcessor {


    private static final Logger log = LoggerFactory.getLogger(InputReceivedProcessor.class);

    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final EventIdIdempotencyGuard idempotencyGuard;
    private final MeterRegistry meterRegistry;

    public InputReceivedProcessor(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            EventIdIdempotencyGuard idempotencyGuard,
            MeterRegistry meterRegistry
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.idempotencyGuard = idempotencyGuard;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
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
