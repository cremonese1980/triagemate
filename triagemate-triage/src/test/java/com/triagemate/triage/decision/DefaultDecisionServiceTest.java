package com.triagemate.triage.decision;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDecisionServiceTest {

    @Test
    void decideReturnsDefaultAccept() {
        DefaultDecisionService service = new DefaultDecisionService();
        DecisionContext.Trace trace = new DecisionContext.Trace("request-1", "corr-1");
        DecisionContext<String> context = new DecisionContext<>(
                "event-1",
                "triagemate.ingest.input-received",
                Instant.EPOCH,
                trace,
                "payload"
        );

        DecisionResult result = service.decide(context);

        assertEquals(DecisionOutcome.ACCEPT, result.outcome());
        assertEquals("default-accept", result.reason());
    }
}
