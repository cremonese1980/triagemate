package com.triagemate.triage.control.routing;

import java.util.Optional;

public class NoOpDecisionReplayService implements DecisionReplayService {

    @Override
    public Optional<DecisionLogEntry> replay(String eventId) {
        return Optional.empty();
    }
}
