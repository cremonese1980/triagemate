package com.triagemate.triage.idempotency;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventIdIdempotencyGuard implements EventIdIdempotencyGuard {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isDuplicate(String eventId) {
        return processed.contains(eventId);
    }

    @Override
    public void markProcessed(String eventId) {
        processed.add(eventId);
    }
}
