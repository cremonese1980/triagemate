package com.triagemate.triage.idempotency;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.Random.class)
class EventIdIdempotencyGuardTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("triagemate_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

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