package com.triagemate.triage.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DecisionRecordRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("triagemate_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private DecisionRecordRepository decisionRecordRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String EVENT_ID = "evt-test-001";
    private static final Instant NOW = Instant.parse("2026-03-17T12:00:00Z");

    @BeforeEach
    void setUp() {
        decisionRecordRepository.deleteAll();
        processedEventRepository.deleteAll();

        // Insert prerequisite processed_event (FK target)
        processedEventRepository.save(new ProcessedEvent(EVENT_ID, NOW));
    }

    @Test
    void save_persistsDecisionRecord() {
        UUID decisionId = UUID.randomUUID();
        DecisionRecord record = new DecisionRecord(
                decisionId, EVENT_ID, "1.0.0", "ACCEPT",
                "ACCEPTED_BY_DEFAULT", "All policies passed; accepted by default",
                "{\"inputId\":\"input-1\",\"channel\":\"email\"}", "{\"strategy\":\"rules-v1\"}",
                NOW
        );

        decisionRecordRepository.save(record);

        Optional<DecisionRecord> found = decisionRecordRepository.findById(decisionId);
        assertThat(found).isPresent();
        assertThat(found.get().getDecisionId()).isEqualTo(decisionId);
        assertThat(found.get().getEventId()).isEqualTo(EVENT_ID);
        assertThat(found.get().getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(found.get().getOutcome()).isEqualTo("ACCEPT");
        assertThat(found.get().getReasonCode()).isEqualTo("ACCEPTED_BY_DEFAULT");
        assertThat(found.get().getHumanReadableReason()).isEqualTo("All policies passed; accepted by default");
        assertThat(found.get().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void save_persistsJsonbColumns() {
        UUID decisionId = UUID.randomUUID();
        String inputJson = "{\"inputId\":\"input-1\",\"channel\":\"email\",\"subject\":\"Test\"}";
        String attrJson = "{\"decisionId\":\"dec-1\",\"strategy\":\"rules-v1\"}";

        DecisionRecord record = new DecisionRecord(
                decisionId, EVENT_ID, "1.0.0", "ACCEPT",
                null, null, inputJson, attrJson, NOW
        );

        decisionRecordRepository.save(record);

        Optional<DecisionRecord> found = decisionRecordRepository.findById(decisionId);
        assertThat(found).isPresent();
        assertThat(found.get().getInputSnapshot()).isEqualTo(inputJson);
        assertThat(found.get().getAttributesSnapshot()).isEqualTo(attrJson);
    }

    @Test
    void save_allowsNullAttributesSnapshot() {
        UUID decisionId = UUID.randomUUID();
        DecisionRecord record = new DecisionRecord(
                decisionId, EVENT_ID, "1.0.0", "ACCEPT",
                null, null, "{}", null, NOW
        );

        decisionRecordRepository.save(record);

        Optional<DecisionRecord> found = decisionRecordRepository.findById(decisionId);
        assertThat(found).isPresent();
        assertThat(found.get().getAttributesSnapshot()).isNull();
    }

    @Test
    void findByEventId_returnsMatchingRecord() {
        UUID decisionId = UUID.randomUUID();
        DecisionRecord record = new DecisionRecord(
                decisionId, EVENT_ID, "1.0.0", "ACCEPT",
                "ACCEPTED_BY_DEFAULT", "Accepted", "{}", null, NOW
        );

        decisionRecordRepository.save(record);

        Optional<DecisionRecord> found = decisionRecordRepository.findByEventId(EVENT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getDecisionId()).isEqualTo(decisionId);
    }

    @Test
    void findByEventId_returnsEmptyForUnknownEvent() {
        Optional<DecisionRecord> found = decisionRecordRepository.findByEventId("evt-nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findByPolicyVersion_returnsMatchingRecords() {
        String eventId2 = "evt-test-002";
        processedEventRepository.save(new ProcessedEvent(eventId2, NOW));

        decisionRecordRepository.save(new DecisionRecord(
                UUID.randomUUID(), EVENT_ID, "1.0.0", "ACCEPT",
                null, null, "{}", null, NOW
        ));
        decisionRecordRepository.save(new DecisionRecord(
                UUID.randomUUID(), eventId2, "1.0.0", "REJECT",
                null, null, "{}", null, NOW
        ));

        List<DecisionRecord> results = decisionRecordRepository.findByPolicyVersion("1.0.0");
        assertThat(results).hasSize(2);
    }

    @Test
    void findByPolicyVersion_returnsEmptyForUnknownVersion() {
        List<DecisionRecord> results = decisionRecordRepository.findByPolicyVersion("99.0.0");
        assertThat(results).isEmpty();
    }

    @Test
    void foreignKeyConstraint_rejectsOrphanDecision() {
        UUID decisionId = UUID.randomUUID();
        DecisionRecord orphan = new DecisionRecord(
                decisionId, "evt-orphan-no-parent", "1.0.0", "ACCEPT",
                null, null, "{}", null, NOW
        );

        assertThatThrownBy(() -> {
            decisionRecordRepository.save(orphan);
            decisionRecordRepository.flush();
        }).isInstanceOf(Exception.class);
    }
}
