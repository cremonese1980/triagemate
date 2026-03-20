package com.triagemate.triage.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DecisionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DecisionPersistenceService.class);

    private final DecisionRecordRepository repository;
    private final ObjectMapper objectMapper;

    public DecisionPersistenceService(DecisionRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void persist(DecisionResult result, DecisionContext<?> context) {
        UUID decisionId = extractDecisionId(result);
        String inputSnapshot = serialize(context.payload(), "input snapshot");
        String attributesSnapshot = result.attributes().isEmpty()
                ? null
                : serialize(result.attributes(), "attributes snapshot");

        DecisionRecord record = new DecisionRecord(
                decisionId,
                context.eventId(),
                result.policyVersion(),
                result.outcome().name(),
                result.reasonCode() != null ? result.reasonCode().name() : null,
                result.humanReadableReason(),
                inputSnapshot,
                attributesSnapshot,
                Instant.now()
        );

        repository.save(record);

        log.info("Decision persisted decisionId={} eventId={} outcome={}",
                decisionId, context.eventId(), result.outcome());
    }

    private UUID extractDecisionId(DecisionResult result) {
        Object raw = result.attributes().get("decisionId");
        if (raw instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return UUID.nameUUIDFromBytes(s.getBytes());
            }
        }
        return UUID.randomUUID();
    }

    private String serialize(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + label, e);
        }
    }
}
