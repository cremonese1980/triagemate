package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.decision.DecisionOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DecisionLogEntryTest {

    @Test
    void fromCreatesEntryWithTimestamp() {
        DecisionLogEntry entry = DecisionLogEntry.from(
                "event-1", "test.event", 1,
                DecisionOutcome.ACCEPT, "accepted",
                Map.of("strategy", "rules-v1")
        );

        assertEquals("event-1", entry.eventId());
        assertEquals("test.event", entry.eventType());
        assertEquals(1, entry.eventVersion());
        assertEquals(DecisionOutcome.ACCEPT, entry.outcome());
        assertEquals("accepted", entry.reason());
        assertNotNull(entry.timestamp());
        assertEquals("rules-v1", entry.policyMetadata().get("strategy"));
        assertEquals(Map.of(), entry.costMetadata());
    }
}
