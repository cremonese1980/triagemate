package com.gabriele.triagemate.contracts.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Versioned, auditable envelope for every event crossing service boundaries.
 *
 * Design goals:
 * - stable over time (payload evolves by adding fields; breaking changes => bump eventVersion or eventType)
 * - traceable (requestId, correlationId, causationId)
 * - safe (no secrets; keep payloads minimal)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,     // e.g. "triagemate.ingest.input-received"
        @JsonProperty("eventVersion") int eventVersion,  // e.g. 1
        @JsonProperty("occurredAt") Instant occurredAt,

        @JsonProperty("producer") Producer producer,
        @JsonProperty("trace") Trace trace,

        @JsonProperty("payload") T payload,
        @JsonProperty("meta") Map<String, Object> meta   // optional extra context, non-breaking
) {
    public record Producer(
            @JsonProperty("service") String service,     // e.g. "triagemate-ingest"
            @JsonProperty("instance") String instance    // optional (hostname/container id)
    ) {}

    public record Trace(
            @JsonProperty("requestId") String requestId,         // HTTP request id (yours)
            @JsonProperty("correlationId") String correlationId, // for cross-service trace
            @JsonProperty("causationId") String causationId      // parent event id, if any
    ) {}
}
