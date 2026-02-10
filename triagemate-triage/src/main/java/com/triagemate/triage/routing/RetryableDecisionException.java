package com.triagemate.triage.routing;

public class RetryableDecisionException extends RuntimeException {

    public RetryableDecisionException(String message) {
        super(message);
    }
}
