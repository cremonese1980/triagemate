package com.triagemate.triage.control.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class DecisionContextFactory {

    private final ObjectMapper objectMapper;

    public DecisionContextFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecisionContext<InputReceivedV1> fromEnvelope(EventEnvelope<?> envelope) {
        Map<String, String> trace = new HashMap<>();
        trace.put("requestId", envelope.trace() != null ? envelope.trace().requestId() : null);
        trace.put("correlationId", envelope.trace() != null ? envelope.trace().correlationId() : null);

        InputReceivedV1 payload = objectMapper.convertValue(envelope.payload(), InputReceivedV1.class);

        return new DecisionContext<>(
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.occurredAt(),
                Collections.unmodifiableMap(trace),
                payload
        );
    }
}
