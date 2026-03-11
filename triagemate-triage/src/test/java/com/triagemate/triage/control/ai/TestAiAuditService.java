package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;

/**
 * Shared test stub for {@link AiAuditService} that replaces duplicate
 * StubAuditService / NoopAuditService / RecordingAuditService inner classes.
 *
 * <p>By default all operations are no-ops. Callers that need to inspect
 * recorded state can read the public fields directly.</p>
 */
class TestAiAuditService extends AiAuditService {

    boolean recordCalled = false;
    boolean errorCalled = false;
    String lastErrorType = null;
    String lastErrorMessage = null;

    TestAiAuditService() {
        super(record -> {});
    }

    @Override
    public void record(DecisionContext<?> context, DecisionResult deterministicResult,
                       AiDecisionAdvice advice, ValidatedAdvice validated) {
        recordCalled = true;
    }

    @Override
    public void recordError(DecisionContext<?> context, DecisionResult deterministicResult,
                            String errorType, String errorMessage) {
        recordError(context, errorType, errorMessage);
    }

    @Override
    public void recordError(DecisionContext<?> context, String errorType, String errorMessage) {
        errorCalled = true;
        lastErrorType = errorType;
        lastErrorMessage = errorMessage;
    }

    void reset() {
        recordCalled = false;
        errorCalled = false;
        lastErrorType = null;
        lastErrorMessage = null;
    }
}
