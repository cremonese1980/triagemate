package com.triagemate.triage.control.decision;

public record ActorContext(
        String actorId,
        String actorType,
        String capabilityGroup
) {
}
