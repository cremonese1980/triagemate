package com.triagemate.triage.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.persistence.DecisionRecord;
import com.triagemate.triage.persistence.DecisionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final DecisionRecordRepository repository;
    private final DecisionService decisionService;
    private final ObjectMapper objectMapper;

    public ReplayService(DecisionRecordRepository repository,
                         @Qualifier("deterministicDecisionService") DecisionService decisionService,
                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.decisionService = decisionService;
        this.objectMapper = objectMapper;
    }

    public ReplayResult replayByDecisionId(UUID decisionId) {
        DecisionRecord original = repository.findById(decisionId)
                .orElseThrow(() -> new DecisionNotFoundException(decisionId));
        return replay(original);
    }

    public ReplayResult replayByEventId(String eventId) {
        DecisionRecord original = repository.findByEventId(eventId)
                .orElseThrow(() -> new DecisionNotFoundException(eventId));
        return replay(original);
    }

    private ReplayResult replay(DecisionRecord original) {
        log.info("Replaying decision decisionId={} eventId={} originalPolicyVersion={}",
                original.getDecisionId(), original.getEventId(), original.getPolicyVersion());

        InputReceivedV1 input = deserializeInput(original.getInputSnapshot());

        DecisionContext<InputReceivedV1> context = DecisionContext.of(
                original.getEventId(),
                "replay",
                1,
                Instant.now(),
                Map.of(),
                input
        );

        DecisionResult newDecision = decisionService.decide(context);

        Map<String, Object> originalAttributes = deserializeAttributes(original.getAttributesSnapshot());

        return ReplayResult.compare(
                original.getDecisionId(),
                original.getOutcome(), original.getPolicyVersion(), originalAttributes,
                newDecision.outcome().name(), newDecision.policyVersion(), newDecision.attributes()
        );
    }

    private InputReceivedV1 deserializeInput(String json) {
        try {
            return objectMapper.readValue(json, InputReceivedV1.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize input snapshot", e);
        }
    }

    private Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize attributes snapshot, returning empty map", e);
            return Map.of();
        }
    }
}
