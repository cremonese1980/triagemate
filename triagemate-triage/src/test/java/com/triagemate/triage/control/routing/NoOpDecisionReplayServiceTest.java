package com.triagemate.triage.control.routing;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoOpDecisionReplayServiceTest {

    @Test
    void replayReturnsEmpty() {
        NoOpDecisionReplayService service = new NoOpDecisionReplayService();

        Optional<DecisionLogEntry> result = service.replay("event-123");

        assertEquals(Optional.empty(), result);
    }
}
