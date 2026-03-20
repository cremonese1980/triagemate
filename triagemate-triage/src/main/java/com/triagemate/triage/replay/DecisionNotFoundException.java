package com.triagemate.triage.replay;

import java.util.UUID;

public class DecisionNotFoundException extends RuntimeException {

    public DecisionNotFoundException(UUID decisionId) {
        super("Decision not found: " + decisionId);
    }

    public DecisionNotFoundException(String eventId) {
        super("Decision not found for event: " + eventId);
    }
}
