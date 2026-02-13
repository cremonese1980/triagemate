package com.triagemate.triage.control.routing;

import java.util.Optional;

public interface DecisionReplayService {
    Optional<DecisionLogEntry> replay(String eventId);
}
