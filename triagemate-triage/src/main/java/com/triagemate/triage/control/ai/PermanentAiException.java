package com.triagemate.triage.control.ai;

/**
 * Permanent AI error — no retry.
 * HTTP 401, HTTP 400, malformed response, confidence below threshold.
 */
public class PermanentAiException extends AiAdvisoryException {

    public PermanentAiException(String message) {
        super(message);
    }

    public PermanentAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
