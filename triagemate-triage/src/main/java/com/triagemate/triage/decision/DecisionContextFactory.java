package com.triagemate.triage.decision;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.springframework.stereotype.Component;

@Component
public class DecisionContextFactory {

    public DecisionContext<InputReceivedV1> fromEnvelope(EventEnvelope<InputReceivedV1> envelope) {
        DecisionContext.Trace trace = new DecisionContext.Trace(
                envelope.trace() != null ? envelope.trace().requestId() : null,
                envelope.trace() != null ? envelope.trace().correlationId() : null
        );

        return new DecisionContext<>(
                envelope.eventId(),
                envelope.eventType(),
                envelope.occurredAt(),
                trace,
                envelope.payload()
        );
    }
}
