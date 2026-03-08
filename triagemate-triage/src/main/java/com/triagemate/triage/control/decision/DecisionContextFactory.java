package com.triagemate.triage.control.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.slf4j.MDC;
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
        // Read from MDC (populated by InputReceivedConsumer with fallback IDs for null traces)
        // This ensures generated fallback IDs flow through to the outbox payload
        Map<String, String> trace = new HashMap<>();
        trace.put("requestId", MDC.get("requestId"));
        trace.put("correlationId", MDC.get("correlationId"));

        InputReceivedV1 payload = objectMapper.convertValue(envelope.payload(), InputReceivedV1.class);

        return DecisionContext.of(
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.occurredAt(),
                Collections.unmodifiableMap(trace),
                payload
        );
    }
}
