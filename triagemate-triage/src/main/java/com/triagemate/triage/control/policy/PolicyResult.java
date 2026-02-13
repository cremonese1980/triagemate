package com.triagemate.triage.control.policy;

import java.util.Map;

public record PolicyResult(
        boolean allowed,
        String reason,
        Map<String, Object> metadata
) {
    public PolicyResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static PolicyResult allow(String reason) {
        return new PolicyResult(true, reason, Map.of());
    }

    public static PolicyResult deny(String reason, Map<String, Object> metadata) {
        return new PolicyResult(false, reason, metadata);
    }
}
