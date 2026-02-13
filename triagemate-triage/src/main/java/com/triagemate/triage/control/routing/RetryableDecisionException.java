package com.triagemate.triage.control.routing;

public class RetryableDecisionException extends RuntimeException {

    public RetryableDecisionException(String message) {
        super(message);
    }
}
