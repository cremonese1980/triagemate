package com.triagemate.ingest.client.dto;

public record TriagemateTriageResponse(
        String service,
        String status,
        Long sleptMs
) {}
