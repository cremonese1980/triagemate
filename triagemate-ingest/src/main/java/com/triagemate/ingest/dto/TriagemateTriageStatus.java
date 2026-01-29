package com.triagemate.ingest.dto;

import com.triagemate.ingest.client.dto.TriagemateTriageResponse;

public record TriagemateTriageStatus(
        TriagemateTriageStatusKind kind,
        String service,
        TriagemateTriageResponse payload,
        String error,
        long latencyMs
) {
    public static TriagemateTriageStatus ok(String service, TriagemateTriageResponse payload, long latencyMs) {
        return new TriagemateTriageStatus(TriagemateTriageStatusKind.OK, service, payload, null, latencyMs);
    }

    public static TriagemateTriageStatus timeout(String service, String error, long latencyMs) {
        return new TriagemateTriageStatus(TriagemateTriageStatusKind.TIMEOUT, service, null, error, latencyMs);
    }

    public static TriagemateTriageStatus unavailable(String service, String error, long latencyMs) {
        return new TriagemateTriageStatus(TriagemateTriageStatusKind.UNAVAILABLE, service, null, error, latencyMs);
    }
}
