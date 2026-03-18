package com.triagemate.triage.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionRecordTest {

    private static final UUID DECISION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EVENT_ID = "evt-001";
    private static final String POLICY_VERSION = "1.0.0";
    private static final String OUTCOME = "ACCEPT";
    private static final String REASON_CODE = "ACCEPTED_BY_DEFAULT";
    private static final String HUMAN_REASON = "All policies passed; accepted by default";
    private static final String INPUT_SNAPSHOT = "{\"inputId\":\"input-1\",\"channel\":\"email\"}";
    private static final String ATTRIBUTES_SNAPSHOT = "{\"decisionId\":\"dec-1\",\"strategy\":\"rules-v1\"}";
    private static final Instant CREATED_AT = Instant.parse("2026-03-17T10:00:00Z");

    @Test
    void constructor_setsAllFields() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                REASON_CODE, HUMAN_REASON, INPUT_SNAPSHOT, ATTRIBUTES_SNAPSHOT, CREATED_AT
        );

        assertThat(record.getDecisionId()).isEqualTo(DECISION_ID);
        assertThat(record.getEventId()).isEqualTo(EVENT_ID);
        assertThat(record.getPolicyVersion()).isEqualTo(POLICY_VERSION);
        assertThat(record.getOutcome()).isEqualTo(OUTCOME);
        assertThat(record.getReasonCode()).isEqualTo(REASON_CODE);
        assertThat(record.getHumanReadableReason()).isEqualTo(HUMAN_REASON);
        assertThat(record.getInputSnapshot()).isEqualTo(INPUT_SNAPSHOT);
        assertThat(record.getAttributesSnapshot()).isEqualTo(ATTRIBUTES_SNAPSHOT);
        assertThat(record.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void constructor_allowsNullableFields() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );

        assertThat(record.getReasonCode()).isNull();
        assertThat(record.getHumanReadableReason()).isNull();
        assertThat(record.getAttributesSnapshot()).isNull();
    }

    @Test
    void equals_sameDecisionId_areEqual() {
        DecisionRecord a = new DecisionRecord(
                DECISION_ID, "evt-a", "1.0.0", "ACCEPT",
                null, null, "{}", null, CREATED_AT
        );
        DecisionRecord b = new DecisionRecord(
                DECISION_ID, "evt-b", "2.0.0", "REJECT",
                null, null, "{}", null, CREATED_AT
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equals_differentDecisionId_areNotEqual() {
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        DecisionRecord a = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );
        DecisionRecord b = new DecisionRecord(
                otherId, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equals_sameInstance_isEqual() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );

        assertThat(record).isEqualTo(record);
    }

    @Test
    void equals_null_isNotEqual() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );

        assertThat(record).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_isNotEqual() {
        DecisionRecord record = new DecisionRecord(
                DECISION_ID, EVENT_ID, POLICY_VERSION, OUTCOME,
                null, null, INPUT_SNAPSHOT, null, CREATED_AT
        );

        assertThat(record).isNotEqualTo("not a DecisionRecord");
    }
}
