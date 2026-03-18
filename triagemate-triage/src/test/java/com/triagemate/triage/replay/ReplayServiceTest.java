package com.triagemate.triage.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.control.decision.ReasonCode;
import com.triagemate.triage.persistence.DecisionRecord;
import com.triagemate.triage.persistence.DecisionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReplayServiceTest {

    private static final UUID DECISION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EVENT_ID = "evt-001";
    private static final String INPUT_JSON = "{\"inputId\":\"input-1\",\"channel\":\"email\",\"subject\":\"Test\",\"text\":\"body\",\"from\":\"user@test.com\",\"receivedAtEpochMs\":1700000000000}";
    private static final String ATTRIBUTES_JSON = "{\"strategy\":\"rules-v1\",\"decisionId\":\"dec-1\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DecisionRecordRepository repository;
    private DecisionService decisionService;
    private ReplayService replayService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DecisionRecordRepository.class);
        decisionService = Mockito.mock(DecisionService.class);
        replayService = new ReplayService(repository, decisionService, objectMapper);
    }

    @Test
    void replayByDecisionId_returnsComparisonWithNoDrift() {
        when(repository.findById(DECISION_ID)).thenReturn(Optional.of(createOriginalRecord("ACCEPT")));
        when(decisionService.decide(any())).thenReturn(DecisionResult.of(
                DecisionOutcome.ACCEPT, "deterministic-default-accept",
                Map.of("decisionId", "new-dec", "strategy", "rules-v1"),
                ReasonCode.ACCEPTED_BY_DEFAULT, "All policies passed", "2.0.0"
        ));

        ReplayResult result = replayService.replayByDecisionId(DECISION_ID);

        assertThat(result.driftDetected()).isFalse();
        assertThat(result.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(result.newOutcome()).isEqualTo("ACCEPT");
        assertThat(result.originalPolicyVersion()).isEqualTo("1.0.0");
        assertThat(result.newPolicyVersion()).isEqualTo("2.0.0");
    }

    @Test
    void replayByDecisionId_detectsDrift() {
        when(repository.findById(DECISION_ID)).thenReturn(Optional.of(createOriginalRecord("ACCEPT")));
        when(decisionService.decide(any())).thenReturn(DecisionResult.of(
                DecisionOutcome.REJECT, "policy-rejected",
                Map.of("decisionId", "new-dec", "strategy", "policy-rejection"),
                ReasonCode.POLICY_REJECTED, "Rejected", "2.0.0"
        ));

        ReplayResult result = replayService.replayByDecisionId(DECISION_ID);

        assertThat(result.driftDetected()).isTrue();
        assertThat(result.originalOutcome()).isEqualTo("ACCEPT");
        assertThat(result.newOutcome()).isEqualTo("REJECT");
    }

    @Test
    void replayByEventId_works() {
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.of(createOriginalRecord("ACCEPT")));
        when(decisionService.decide(any())).thenReturn(DecisionResult.of(
                DecisionOutcome.ACCEPT, "default-accept",
                Map.of("decisionId", "new-dec", "strategy", "rules-v1"),
                ReasonCode.ACCEPTED_BY_DEFAULT, "OK", "1.0.0"
        ));

        ReplayResult result = replayService.replayByEventId(EVENT_ID);

        assertThat(result.originalDecisionId()).isEqualTo(DECISION_ID);
        assertThat(result.driftDetected()).isFalse();
    }

    @Test
    void replayByDecisionId_throwsWhenNotFound() {
        when(repository.findById(DECISION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> replayService.replayByDecisionId(DECISION_ID))
                .isInstanceOf(DecisionNotFoundException.class)
                .hasMessageContaining(DECISION_ID.toString());
    }

    @Test
    void replayByEventId_throwsWhenNotFound() {
        when(repository.findByEventId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> replayService.replayByEventId("unknown"))
                .isInstanceOf(DecisionNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void replayDeserializesAttributesSnapshot() {
        when(repository.findById(DECISION_ID)).thenReturn(Optional.of(createOriginalRecord("ACCEPT")));
        when(decisionService.decide(any())).thenReturn(DecisionResult.of(
                DecisionOutcome.ACCEPT, "default-accept",
                Map.of("decisionId", "new-dec", "strategy", "rules-v2"),
                ReasonCode.ACCEPTED_BY_DEFAULT, "OK", "1.0.0"
        ));

        ReplayResult result = replayService.replayByDecisionId(DECISION_ID);

        assertThat(result.originalAttributes()).containsEntry("strategy", "rules-v1");
        assertThat(result.attributeDifferences()).isNotEmpty();
    }

    @Test
    void replayHandlesNullAttributesSnapshot() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, "1.0.0", "ACCEPT",
                null, null, INPUT_JSON, null, Instant.now()
        );
        when(repository.findById(DECISION_ID)).thenReturn(Optional.of(record));
        when(decisionService.decide(any())).thenReturn(DecisionResult.of(
                DecisionOutcome.ACCEPT, "default-accept",
                Map.of("decisionId", "new-dec"), ReasonCode.ACCEPTED_BY_DEFAULT, "OK", "1.0.0"
        ));

        ReplayResult result = replayService.replayByDecisionId(DECISION_ID);

        assertThat(result.originalAttributes()).isEmpty();
    }

    private DecisionRecord createOriginalRecord(String outcome) {
        return new DecisionRecord(
                DECISION_ID, EVENT_ID, "1.0.0", outcome,
                "ACCEPTED_BY_DEFAULT", "All policies passed",
                INPUT_JSON, ATTRIBUTES_JSON, Instant.now()
        );
    }
}
