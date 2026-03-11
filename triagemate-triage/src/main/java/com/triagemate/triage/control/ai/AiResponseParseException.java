package com.triagemate.triage.control.ai;

/**
 * AI response could not be parsed into structured output.
 * Permanent error — triggers fallback to {@link AiDecisionAdvice#NONE}.
 */
public class AiResponseParseException extends PermanentAiException {

    public AiResponseParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
