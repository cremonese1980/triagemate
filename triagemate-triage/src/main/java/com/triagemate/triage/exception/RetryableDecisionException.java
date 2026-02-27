package com.triagemate.triage.exception;

public class RetryableDecisionException extends RuntimeException {

    public RetryableDecisionException(String message) {
        super(message);
    }
}
