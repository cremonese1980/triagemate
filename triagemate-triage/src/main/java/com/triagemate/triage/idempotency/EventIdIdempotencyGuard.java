package com.triagemate.triage.idempotency;

public interface EventIdIdempotencyGuard {

    boolean isDuplicate(String eventId);

    void markProcessed(String eventId);
}
