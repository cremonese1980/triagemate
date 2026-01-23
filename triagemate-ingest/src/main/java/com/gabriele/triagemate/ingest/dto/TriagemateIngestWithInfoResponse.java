package com.gabriele.triagemate.ingest.dto;

import java.time.Instant;

public record TriagemateIngestWithInfoResponse(
        String service,
        int port,
        Instant timestamp,
        int infoServiceTimeoutMs,
        TriagemateTriageStatus info
) {}
