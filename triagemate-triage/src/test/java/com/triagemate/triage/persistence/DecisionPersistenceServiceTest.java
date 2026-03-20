package com.triagemate.triage.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.config.ObjectMapperConfig;
import com.triagemate.triage.control.decision.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DecisionPersistenceServiceTest {

    @Mock
    private DecisionRecordRepository repository;

    @Captor
    private ArgumentCaptor<DecisionRecord> recordCaptor;

    private DecisionPersistenceService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapperConfig().objectMapper();
        service = new DecisionPersistenceService(repository, objectMapper);
    }

    private DecisionContext<String> context(String eventId) {
        return DecisionContext.of(
                eventId,
                "triagemate.ingest.input-received",
                1,
                Instant.parse("2026-03-17T12:00:00Z"),
                Map.of("correlationId", "corr-1", "requestId", "req-1"),
                "test-payload"
        );
    }

    @Test
    void persistsDecisionRecordWithAllFields() {
        UUID decisionId = UUID.randomUUID();
        Map<String, Object> attrs = Map.of("decisionId", decisionId.toString(), "extra", "value");
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "accepted", attrs,
                ReasonCode.ACCEPTED_BY_DEFAULT, "Accepted by default policy", "1.0.0"
        );

        service.persist(result, context("evt-1"));

        verify(repository).save(recordCaptor.capture());
        DecisionRecord record = recordCaptor.getValue();

        assertThat(record.getDecisionId()).isEqualTo(decisionId);
        assertThat(record.getEventId()).isEqualTo("evt-1");
        assertThat(record.getPolicyVersion()).isEqualTo("1.0.0");
        assertThat(record.getOutcome()).isEqualTo("ACCEPT");
        assertThat(record.getReasonCode()).isEqualTo("ACCEPTED_BY_DEFAULT");
        assertThat(record.getHumanReadableReason()).isEqualTo("Accepted by default policy");
        assertThat(record.getInputSnapshot()).isEqualTo("\"test-payload\"");
        assertThat(record.getAttributesSnapshot()).isNotNull();
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsNullReasonCodeAsNull() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.REJECT, "rejected", Map.of("decisionId", UUID.randomUUID().toString())
        );

        service.persist(result, context("evt-2"));

        verify(repository).save(recordCaptor.capture());
        DecisionRecord record = recordCaptor.getValue();

        assertThat(record.getReasonCode()).isNull();
        assertThat(record.getHumanReadableReason()).isNull();
    }

    @Test
    void persistsNullAttributesSnapshotWhenEmpty() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.DEFER, "deferred", Map.of());

        service.persist(result, context("evt-3"));

        verify(repository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getAttributesSnapshot()).isNull();
    }

    @Test
    void generatesRandomUuidWhenDecisionIdNotInAttributes() {
        DecisionResult result = DecisionResult.of(DecisionOutcome.ACCEPT, "ok", Map.of());

        service.persist(result, context("evt-4"));

        verify(repository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getDecisionId()).isNotNull();
    }

    @Test
    void generatesNameBasedUuidForNonUuidDecisionId() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.ACCEPT, "ok", Map.of("decisionId", "not-a-uuid")
        );

        service.persist(result, context("evt-5"));

        verify(repository).save(recordCaptor.capture());
        UUID expected = UUID.nameUUIDFromBytes("not-a-uuid".getBytes());
        assertThat(recordCaptor.getValue().getDecisionId()).isEqualTo(expected);
    }

    @Test
    void deterministicSerialization_sameAttributesDifferentInsertionOrder() throws Exception {
        UUID decisionId = UUID.randomUUID();

        Map<String, Object> attrs1 = new LinkedHashMap<>();
        attrs1.put("decisionId", decisionId.toString());
        attrs1.put("zebra", "z");
        attrs1.put("alpha", "a");

        Map<String, Object> attrs2 = new LinkedHashMap<>();
        attrs2.put("alpha", "a");
        attrs2.put("decisionId", decisionId.toString());
        attrs2.put("zebra", "z");

        DecisionResult result1 = DecisionResult.of(DecisionOutcome.ACCEPT, "ok", attrs1);
        DecisionResult result2 = DecisionResult.of(DecisionOutcome.ACCEPT, "ok", attrs2);

        service.persist(result1, context("evt-6a"));
        service.persist(result2, context("evt-6b"));

        verify(repository, org.mockito.Mockito.times(2)).save(recordCaptor.capture());
        var records = recordCaptor.getAllValues();

        assertThat(records.get(0).getAttributesSnapshot())
                .isEqualTo(records.get(1).getAttributesSnapshot());
    }

    @Test
    void inputSnapshotSerializesPayload() {
        Map<String, String> payload = Map.of("key", "value");
        DecisionContext<Map<String, String>> ctx = DecisionContext.of(
                "evt-7", "test-type", 1,
                Instant.parse("2026-03-17T12:00:00Z"),
                Map.of(), payload
        );
        DecisionResult result = DecisionResult.of(DecisionOutcome.ACCEPT, "ok", Map.of());

        service.persist(result, ctx);

        verify(repository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getInputSnapshot()).isEqualTo("{\"key\":\"value\"}");
    }
}
