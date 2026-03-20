package com.triagemate.triage.control.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.config.ObjectMapperConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionContextFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();
    private final DecisionContextFactory factory = new DecisionContextFactory(objectMapper);

    @Test
    void fromEnvelope_ignoresUnknownPayloadFields() {
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
                "evt-extra-field",
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2026-03-18T10:15:30Z"),
                new EventEnvelope.Producer("triagemate-ingest", "test"),
                new EventEnvelope.Trace("req-1", "corr-1", null),
                Map.of(
                        "inputId", "input-1",
                        "channel", "email",
                        "subject", "hello",
                        "text", "world",
                        "from", "from@test",
                        "receivedAtEpochMs", 1700000000000L,
                        "cost", 123.45
                ),
                Map.of()
        );

        DecisionContext<InputReceivedV1> context = factory.fromEnvelope(envelope);

        assertThat(context.payload().inputId()).isEqualTo("input-1");
        assertThat(context.payload().channel()).isEqualTo("email");
        assertThat(context.payload().receivedAtEpochMs()).isEqualTo(1700000000000L);
    }
}
