package com.triagemate.triage.idempotency;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcEventIdIdempotencyGuard.class)
@TestMethodOrder(MethodOrderer.Random.class)
class EventIdIdempotencyGuardTest extends JdbcIntegrationTestBase {

    @Autowired
    private EventIdIdempotencyGuard guard;

    @Test
    void firstTime_eventIsNotDuplicate() {
        assertThat(guard.tryMarkProcessed("event-1")).isTrue();
    }

    @Test
    void afterMarkProcessed_eventBecomesDuplicate() {
        assertThat(guard.tryMarkProcessed("event-1")).isTrue();
        assertThat(guard.tryMarkProcessed("event-1")).isFalse();
    }

    @Test
    void differentEventIds_areIndependent() {
        guard.tryMarkProcessed("event-1");
        assertThat(guard.tryMarkProcessed("event-2")).isTrue();
    }
}