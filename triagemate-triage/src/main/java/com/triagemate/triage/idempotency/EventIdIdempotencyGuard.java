package com.triagemate.triage.idempotency;

public interface EventIdIdempotencyGuard {

    /**
     * @return true if this call successfully claims the eventId
     *         false if it was already processed
     */
    boolean tryMarkProcessed(String eventId);
}
