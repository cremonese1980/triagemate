package com.triagemate.triage.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdIdempotencyGuardTest {

    private EventIdIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InMemoryEventIdIdempotencyGuard();
    }

    @Test
    void firstTime_eventIsNotDuplicate() {
        assertThat(guard.isDuplicate("event-1")).isFalse();
    }

    @Test
    void afterMarkProcessed_eventBecomesDuplicate() {
        assertThat(guard.isDuplicate("event-1")).isFalse();
        guard.markProcessed("event-1");
        assertThat(guard.isDuplicate("event-1")).isTrue();
    }

    @Test
    void differentEventIds_areIndependent() {
        guard.markProcessed("event-1");
        assertThat(guard.isDuplicate("event-2")).isFalse();
    }

}
