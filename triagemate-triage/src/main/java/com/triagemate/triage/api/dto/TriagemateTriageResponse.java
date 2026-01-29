package com.triagemate.triage.api.dto;

public record TriagemateTriageResponse(
        String service,
        String status,
        Long sleptMs
) {
    public static TriagemateTriageResponse ok() {

        return new TriagemateTriageResponse("info-service", "OK", null);
    }

    public static TriagemateTriageResponse ok(long sleptMs) {
        return new TriagemateTriageResponse("info-service", "OK", sleptMs);
    }
}
