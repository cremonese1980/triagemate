package com.triagemate.triage.control.ai;

/**
 * Base exception for AI advisory errors.
 */
public abstract class AiAdvisoryException extends RuntimeException {

    protected AiAdvisoryException(String message) {
        super(message);
    }

    protected AiAdvisoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
