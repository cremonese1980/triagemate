package com.triagemate.triage.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class DecisionRecord {

    @Id
    @Column(name = "decision_id", nullable = false, updatable = false)
    private UUID decisionId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "policy_version", nullable = false, length = 50, updatable = false)
    private String policyVersion;

    @Column(name = "outcome", nullable = false, length = 50, updatable = false)
    private String outcome;

    @Column(name = "reason_code", length = 100, updatable = false)
    private String reasonCode;

    @Column(name = "human_readable_reason", columnDefinition = "TEXT", updatable = false)
    private String humanReadableReason;

    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String inputSnapshot;

    @Column(name = "attributes_snapshot", columnDefinition = "jsonb", updatable = false)
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String attributesSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DecisionRecord() {
        // JPA only
    }

    public DecisionRecord(
            UUID decisionId,
            String eventId,
            String policyVersion,
            String outcome,
            String reasonCode,
            String humanReadableReason,
            String inputSnapshot,
            String attributesSnapshot,
            Instant createdAt
    ) {
        this.decisionId = decisionId;
        this.eventId = eventId;
        this.policyVersion = policyVersion;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.humanReadableReason = humanReadableReason;
        this.inputSnapshot = inputSnapshot;
        this.attributesSnapshot = attributesSnapshot;
        this.createdAt = createdAt;
    }

    public UUID getDecisionId() { return decisionId; }
    public String getEventId() { return eventId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getOutcome() { return outcome; }
    public String getReasonCode() { return reasonCode; }
    public String getHumanReadableReason() { return humanReadableReason; }
    public String getInputSnapshot() { return inputSnapshot; }
    public String getAttributesSnapshot() { return attributesSnapshot; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecisionRecord that)) return false;
        return Objects.equals(decisionId, that.decisionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decisionId);
    }
}
