package com.triagemate.ingest.app;

public class KafkaPublishFailedException extends RuntimeException {
    public KafkaPublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
