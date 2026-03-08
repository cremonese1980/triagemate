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

    /**
     * Builds a {@link DecisionContext} from the given envelope, reading trace
     * identifiers (requestId, correlationId) from the current thread's MDC.
     *
     * @implNote This method expects MDC to be populated by the upstream Kafka
     *     consumer ({@code InputReceivedConsumer}) before invocation. If called
     *     outside that pipeline, trace values will be {@code null}.
     */
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
