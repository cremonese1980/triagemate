package com.triagemate.triage.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.control.decision.CostDecision;
import com.triagemate.triage.control.decision.CostGuard;
import com.triagemate.triage.control.policy.PolicyVersionProvider;
import com.triagemate.triage.outbox.OutboxStatus;
import com.triagemate.triage.persistence.DecisionRecord;
import com.triagemate.triage.persistence.DecisionRecordRepository;
import com.triagemate.triage.replay.ReplayResult;
import com.triagemate.triage.replay.ReplayService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class Phase13VerificationIT extends KafkaIntegrationTestBase {

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private DecisionRecordRepository decisionRecordRepository;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${triagemate.kafka.topics.input-received}")
    private String inputTopic;

    @MockBean
    private PolicyVersionProvider policyVersionProvider;

    @MockBean
    private CostGuard costGuard;

    @BeforeEach
    void setUp() {
        when(policyVersionProvider.currentVersion()).thenReturn("1.0.0");
        when(costGuard.evaluateCost(any())).thenReturn(CostDecision.allow(0.0, "within-budget"));
    }

    @Test
    void eventProcessed_decisionRowPersisted_withPolicyVersion() {
        String eventId = UUID.randomUUID().toString();
        sendValidEvent(eventId, "input-persist-001");

        DecisionRecord decision = awaitDecision(eventId);

        assertThat(decision.getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(decision.getOutcome()).isEqualTo("ACCEPT");
        assertThat(decision.getReasonCode()).isEqualTo("ACCEPTED_BY_DEFAULT");
    }

    @Test
    void policyVersionChanged_replayDetectsDrift() {
        String eventId = UUID.randomUUID().toString();
        sendValidEvent(eventId, "input-drift-001");

        DecisionRecord original = awaitDecision(eventId);
        assertThat(original.getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(original.getOutcome()).isEqualTo("ACCEPT");

        when(policyVersionProvider.currentVersion()).thenReturn("2.0.0");
        when(costGuard.evaluateCost(any())).thenReturn(CostDecision.deny(250.0, "over-budget-after-policy-change"));

        ReplayResult replay = replayService.replayByDecisionId(original.getDecisionId());

        assertThat(replay.driftDetected()).isTrue();
        assertThat(replay.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(replay.newOutcome()).isEqualTo("REJECT");
        assertThat(replay.originalPolicyVersion()).isEqualTo("1.0.0");
        assertThat(replay.newPolicyVersion()).isEqualTo("2.0.0");
    }

    @Test
    void phase9Through13Invariants_stillWork() throws Exception {
        String eventId = UUID.randomUUID().toString();
        sendValidEvent(eventId, "input-compat-001");

        DecisionRecord decision = awaitDecision(eventId);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Integer processedCount = jdbcTemplate.queryForObject(
                            "select count(*) from processed_events where event_id = ?",
                            Integer.class,
                            eventId
                    );

                    Integer outboxCount = jdbcTemplate.queryForObject(
                            "select count(*) from outbox_events where aggregate_id = ? and status = ?",
                            Integer.class,
                            decision.getDecisionId().toString(),
                            OutboxStatus.SENT.name()
                    );

                    assertThat(processedCount).isEqualTo(1);
                    assertThat(outboxCount).isEqualTo(1);
                });

        String payload = jdbcTemplate.queryForObject(
                "select payload::text from outbox_events where aggregate_id = ?",
                String.class,
                decision.getDecisionId().toString()
        );

        assertThat(payload).contains("\"policyVersion\":\"1.0.0\"");
        assertThat(decisionRecordRepository.findByEventId(eventId)).contains(decision);
    }

    @Test
    void decisionPersisted_hasCompleteArtifacts_andReplayRemainsDeterministic() throws Exception {
        String eventId = UUID.randomUUID().toString();
        sendValidEvent(eventId, "input-audit-001");

        DecisionRecord decision = awaitDecision(eventId);

        assertThat(decision.getDecisionId()).isNotNull();
        assertThat(decision.getEventId()).isEqualTo(eventId);
        assertThat(decision.getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(decision.getOutcome()).isEqualTo("ACCEPT");
        assertThat(decision.getReasonCode()).isEqualTo("ACCEPTED_BY_DEFAULT");
        assertThat(decision.getHumanReadableReason()).isEqualTo("All policies passed; accepted by default");
        assertThat(decision.getInputSnapshot()).isNotBlank();
        assertThat(decision.getAttributesSnapshot()).isNotBlank();
        assertThat(decision.getCreatedAt()).isNotNull();

        Map<?, ?> inputSnapshot = objectMapper.readValue(decision.getInputSnapshot(), Map.class);
        Map<?, ?> attributesSnapshot = objectMapper.readValue(decision.getAttributesSnapshot(), Map.class);

        assertThat(inputSnapshot).containsEntry("inputId", "input-audit-001");
        assertThat(attributesSnapshot).containsEntry("strategy", "rules-v1");
        assertThat(attributesSnapshot).containsKey("decisionId");

        ReplayResult replay = replayService.replayByEventId(eventId);

        assertThat(replay.driftDetected()).isFalse();
        assertThat(replay.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(replay.newOutcome()).isEqualTo("ACCEPT");
        assertThat(replay.originalPolicyVersion()).isEqualTo("1.0.0");
        assertThat(replay.newPolicyVersion()).isEqualTo("1.0.0");
    }

    private DecisionRecord awaitDecision(String eventId) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(decisionRecordRepository.findByEventId(eventId)).isPresent());

        Optional<DecisionRecord> decision = decisionRecordRepository.findByEventId(eventId);
        assertThat(decision).isPresent();
        return decision.orElseThrow();
    }

    private void sendValidEvent(String eventId, String inputId) {
        InputReceivedV1 payload = new InputReceivedV1(
                inputId,
                "email",
                "subject",
                "hello world",
                "from@test",
                1700000000000L
        );

        EventEnvelope<InputReceivedV1> envelope = new EventEnvelope<>(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2026-03-18T10:15:30Z"),
                new EventEnvelope.Producer("triagemate-ingest", "phase13-it"),
                new EventEnvelope.Trace("req-" + inputId, "corr-" + inputId, null),
                payload,
                Map.of()
        );

        kafkaTemplate.send(inputTopic, eventId, envelope);
    }
}
