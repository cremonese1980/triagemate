package com.triagemate.triage.control.decision;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DecisionContextFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void fromEnvelopeMapsFields() {
        DecisionContextFactory factory = new DecisionContextFactory(objectMapper);
        InputReceivedV1 payload = new InputReceivedV1("input-1", "email", "subject", "text", "from", 123L);
        EventEnvelope.Trace trace = new EventEnvelope.Trace("request-1", "corr-1", "cause-1");
        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                "event-1",
                "triagemate.ingest.input-received",
                1,
                Instant.EPOCH,
                null,
                trace,
                payload,
                Map.of()
        );

        DecisionContext<InputReceivedV1> context = factory.fromEnvelope(envelope);

        assertEquals("event-1", context.eventId());
        assertEquals("triagemate.ingest.input-received", context.eventType());
        assertEquals(1, context.eventVersion());
        assertEquals(Instant.EPOCH, context.occurredAt());
        assertEquals(payload, context.payload());
        assertEquals("request-1", context.trace().get("requestId"));
        assertEquals("corr-1", context.trace().get("correlationId"));
    }

    @Test
    void fromEnvelopeHandlesMissingTrace() {
        DecisionContextFactory factory = new DecisionContextFactory(objectMapper);
        InputReceivedV1 payload = new InputReceivedV1("input-2", "ticket", "subject", "text", "from", 456L);
        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                "event-2",
                "triagemate.ingest.input-received",
                2,
                Instant.EPOCH,
                null,
                null,
                payload,
                Map.of()
        );

        DecisionContext<InputReceivedV1> context = factory.fromEnvelope(envelope);

        assertNull(context.trace().get("requestId"));
        assertNull(context.trace().get("correlationId"));
    }
}
