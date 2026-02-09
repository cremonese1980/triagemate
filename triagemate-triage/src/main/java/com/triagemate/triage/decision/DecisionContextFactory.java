package com.triagemate.triage.decision;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class DecisionContextFactory {

    public DecisionContext<InputReceivedV1> fromEnvelope(EventEnvelope<InputReceivedV1> envelope) {
        Map<String, String> trace = new HashMap<>();
        trace.put("requestId", envelope.trace() != null ? envelope.trace().requestId() : null);
        trace.put("correlationId", envelope.trace() != null ? envelope.trace().correlationId() : null);

        return new DecisionContext<>(
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.occurredAt(),
                Collections.unmodifiableMap(trace),
                envelope.payload()
        );
    }
}
