package com.triagemate.triage.control.ai;

/**
 * Transient AI error — eligible for retry.
 * Network timeout, HTTP 429, HTTP 503.
 */
public class TransientAiException extends AiAdvisoryException {

    public TransientAiException(String message) {
        super(message);
    }

    public TransientAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
