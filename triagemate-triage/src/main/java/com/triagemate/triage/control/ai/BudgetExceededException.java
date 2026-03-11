package com.triagemate.triage.control.ai;

/**
 * Budget limit exceeded — no retry, immediate fallback.
 * Per-decision or daily cost limit breached.
 */
public class BudgetExceededException extends AiAdvisoryException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
