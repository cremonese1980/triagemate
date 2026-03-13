package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiAuditServiceTest {

    @Test
    void record_shouldPersistAdviceWithDecisionIdFromAttributes() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        AiAuditService service = new AiAuditService(repository);

        DecisionContext<?> context = DecisionContext.of(
                "evt-001", "triagemate.ingest.input-received", 1, Instant.now(),
                Map.of("requestId", "req-001"), "payload"
        );

        DecisionResult deterministic = DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic",
                Map.of("decisionId", "dec-777"),
                ReasonCode.ACCEPTED_BY_DEFAULT,
                "All policies passed"
        );

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "DEVICE_ERROR", 0.91, "Strong AI signal", true,
                "anthropic", "claude", "sonnet-4", "1.0.1", "hash",
                12, 22, 0.002, 145
        );

        service.record(context, deterministic, advice, ValidatedAdvice.accepted(advice));

        verify(repository).save(argThat(record ->
                record.decisionId().equals("dec-777")
                        && record.eventId().equals("evt-001")
                        && record.provider().equals("anthropic")
                        && Boolean.TRUE.equals(record.acceptedByValidator())
                        && record.errorType() == null
        ));
    }

    @Test
    void record_shouldFallbackDecisionIdToRequestIdAndEventId() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        AiAuditService service = new AiAuditService(repository);

        DecisionContext<?> contextWithRequest = DecisionContext.of(
                "evt-002", "type", 1, Instant.now(), Map.of("requestId", "req-999"), "payload"
        );
        DecisionContext<?> contextWithoutTrace = DecisionContext.of(
                "evt-003", "type", 1, Instant.now(), Map.of(), "payload"
        );

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "NORMAL", 0.71, "weak", false,
                "anthropic", "claude", "sonnet-4", "1.0.1", "hash",
                1, 1, 0.0, 10
        );

        service.record(contextWithRequest,
                DecisionResult.of(DecisionOutcome.ACCEPT, "det", Map.of(), ReasonCode.ACCEPTED_BY_DEFAULT, "ok"),
                advice,
                ValidatedAdvice.advisory(advice));

        service.record(contextWithoutTrace,
                DecisionResult.of(DecisionOutcome.ACCEPT, "det", Map.of(), ReasonCode.ACCEPTED_BY_DEFAULT, "ok"),
                advice,
                ValidatedAdvice.advisory(advice));

        verify(repository).save(argThat(record -> record.decisionId().equals("req-999") && record.eventId().equals("evt-002")));
        verify(repository).save(argThat(record -> record.decisionId().equals("evt-003") && record.eventId().equals("evt-003")));
    }

    @Test
    void recordError_shouldPersistErrorAuditRow() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        AiAuditService service = new AiAuditService(repository);

        DecisionContext<?> context = DecisionContext.of(
                "evt-004", "type", 1, Instant.now(), Map.of("requestId", "req-004"), "payload"
        );

        service.recordError(context, "TIMEOUT", "provider timed out");

        verify(repository).save(argThat(record ->
                record.decisionId().equals("req-004")
                        && record.errorType().equals("TIMEOUT")
                        && record.errorMessage().equals("provider timed out")
                        && record.provider() == null
        ));
    }

    @Test
    void record_shouldPersistRejectedAdviceWithRejectionReason() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        AiAuditService service = new AiAuditService(repository);

        DecisionContext<?> context = DecisionContext.of(
                "evt-006", "type", 1, Instant.now(), Map.of(), "payload"
        );

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "UNKNOWN_CLASS", 0.55, "Low confidence AI signal", false,
                "anthropic", "claude", "sonnet-4", "1.0.1", "hash",
                8, 15, 0.001, 90
        );

        service.record(context,
                DecisionResult.of(DecisionOutcome.ACCEPT, "det", Map.of(), ReasonCode.ACCEPTED_BY_DEFAULT, "ok"),
                advice,
                ValidatedAdvice.rejected(advice, "Confidence 0.55 below minimum 0.70"));

        verify(repository).save(argThat(record ->
                record.eventId().equals("evt-006")
                        && Boolean.FALSE.equals(record.acceptedByValidator())
                        && record.rejectionReason().equals("Confidence 0.55 below minimum 0.70")
                        && record.suggestedClassification().equals("UNKNOWN_CLASS")
                        && record.reasoning().equals("Low confidence AI signal")
                        && record.inputTokens() == 8
                        && record.outputTokens() == 15
        ));
    }

    @Test
    void record_shouldPreserveAllFieldsFromAdvice() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        AiAuditService service = new AiAuditService(repository);

        DecisionContext<?> context = DecisionContext.of(
                "evt-007", "type", 1, Instant.now(), Map.of(), "payload"
        );

        AiDecisionAdvice advice = new AiDecisionAdvice(
                "DEVICE_ERROR", 0.95, "Strong signal from event features", true,
                "anthropic", "claude-sonnet", "sonnet-4-20250514", "1.0.1", "abc123hash",
                42, 88, 0.0047, 234
        );

        service.record(context,
                DecisionResult.of(DecisionOutcome.ACCEPT, "det", Map.of(), ReasonCode.ACCEPTED_BY_DEFAULT, "ok"),
                advice,
                ValidatedAdvice.accepted(advice));

        verify(repository).save(argThat(record ->
                record.provider().equals("anthropic")
                        && record.model().equals("claude-sonnet")
                        && record.modelVersion().equals("sonnet-4-20250514")
                        && record.promptVersion().equals("1.0.1")
                        && record.promptHash().equals("abc123hash")
                        && record.confidence() == 0.95
                        && record.suggestedClassification().equals("DEVICE_ERROR")
                        && Boolean.TRUE.equals(record.recommendsOverride())
                        && record.reasoning().equals("Strong signal from event features")
                        && record.inputTokens() == 42
                        && record.outputTokens() == 88
                        && Math.abs(record.costUsd() - 0.0047) < 0.0001
                        && record.latencyMs() == 234
                        && record.errorType() == null
                        && record.errorMessage() == null
        ));
    }

    @Test
    void record_shouldSwallowPersistenceExceptions() {
        AiAuditRepository repository = mock(AiAuditRepository.class);
        doThrow(new RuntimeException("db down")).when(repository).save(any(AiAuditRecord.class));

        AiAuditService service = new AiAuditService(repository);
        DecisionContext<?> context = DecisionContext.of("evt-005", "type", 1, Instant.now(), Map.of(), "payload");
        AiDecisionAdvice advice = new AiDecisionAdvice(
                "NORMAL", 0.8, "reason", false,
                "provider", "model", "v1", "1.0.1", "hash", 0, 0, 0.0, 0
        );

        service.record(context,
                DecisionResult.of(DecisionOutcome.ACCEPT, "det", Map.of(), ReasonCode.ACCEPTED_BY_DEFAULT, "ok"),
                advice,
                ValidatedAdvice.advisory(advice));

        verify(repository, times(1)).save(any(AiAuditRecord.class));
    }
}
