package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);

    private final AiAuditRepository repository;
    private final Counter persistenceFailures;

    public AiAuditService(AiAuditRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.persistenceFailures = Counter.builder("triagemate.ai.audit.persistence.failures.total")
                .description("Number of AI audit record persistence failures")
                .register(meterRegistry);
    }

    // Test-friendly constructor (no metrics)
    protected AiAuditService(AiAuditRepository repository) {
        this.repository = repository;
        this.persistenceFailures = null;
    }

    public void record(
            DecisionContext<?> context,
            DecisionResult deterministicResult,
            AiDecisionAdvice advice,
            ValidatedAdvice validated
    ) {
        String decisionId = resolveDecisionId(context, deterministicResult);
        AiAuditRecord record = AiAuditRecord.fromAdvice(
                decisionId,
                context.eventId(),
                advice,
                validated
        );
        saveSafely(context.eventId(), record);
    }

    public void recordError(DecisionContext<?> context, String errorType, String errorMessage) {
        recordError(context, null, errorType, errorMessage);
    }

    public void recordError(
            DecisionContext<?> context,
            DecisionResult deterministicResult,
            String errorType,
            String errorMessage
    ) {
        String decisionId = resolveDecisionId(context, deterministicResult);
        AiAuditRecord record = AiAuditRecord.fromError(
                decisionId,
                context.eventId(),
                errorType,
                errorMessage
        );
        saveSafely(context.eventId(), record);
    }

    private void saveSafely(String eventId, AiAuditRecord record) {
        try {
            repository.save(record);
        } catch (Exception e) {
            if (persistenceFailures != null) {
                persistenceFailures.increment();
            }
            log.error("Failed to persist AI audit record for event {}: {}", eventId, e.getMessage());
        }
    }

    private String resolveDecisionId(DecisionContext<?> context, DecisionResult deterministicResult) {
        if (deterministicResult != null) {
            Map<String, Object> attributes = deterministicResult.attributes();
            if (attributes != null && attributes.get("decisionId") instanceof String decisionId && !decisionId.isBlank()) {
                return decisionId;
            }
        }
        if (context.trace() != null) {
            String requestId = context.trace().get("requestId");
            if (requestId != null && !requestId.isBlank()) {
                return requestId;
            }
        }
        return context.eventId();
    }
}
