package com.triagemate.triage.idempotency;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcEventIdIdempotencyGuard.class)
@TestMethodOrder(MethodOrderer.Random.class)
class EventIdIdempotencyGuardTest extends JdbcIntegrationTestBase {

    @Autowired
    private EventIdIdempotencyGuard guard;

    @Test
    void firstTime_eventIsNotDuplicate() {
        String eventId = UUID.randomUUID().toString();
        assertThat(guard.tryMarkProcessed(eventId)).isTrue();
    }

    @Test
    void afterMarkProcessed_eventBecomesDuplicate() {
        String eventId = UUID.randomUUID().toString();
        assertThat(guard.tryMarkProcessed(eventId)).isTrue();
        assertThat(guard.tryMarkProcessed(eventId)).isFalse();
    }

    @Test
    void differentEventIds_areIndependent() {
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();
        guard.tryMarkProcessed(eventId1);
        assertThat(guard.tryMarkProcessed(eventId2)).isTrue();
    }
}
