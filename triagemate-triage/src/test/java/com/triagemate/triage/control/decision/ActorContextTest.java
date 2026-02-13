package com.triagemate.triage.control.decision;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActorContextTest {

    @Test
    void recordFieldsAccessible() {
        ActorContext actor = new ActorContext("user-123", "human", "support-tier-1");

        assertEquals("user-123", actor.actorId());
        assertEquals("human", actor.actorType());
        assertEquals("support-tier-1", actor.capabilityGroup());
    }

    @Test
    void decisionContextWithActorContext() {
        ActorContext actor = new ActorContext("agent-1", "ai", "triage-bot");
        DecisionContext<String> context = new DecisionContext<>(
                "event-1", "test.event", 1, Instant.EPOCH,
                Map.of(), "payload", actor
        );

        assertEquals(actor, context.actorContext());
        assertEquals("agent-1", context.actorContext().actorId());
    }

    @Test
    void decisionContextWithoutActorContext() {
        DecisionContext<String> context = DecisionContext.of(
                "event-1", "test.event", 1, Instant.EPOCH,
                Map.of(), "payload"
        );

        assertNull(context.actorContext());
    }
}
