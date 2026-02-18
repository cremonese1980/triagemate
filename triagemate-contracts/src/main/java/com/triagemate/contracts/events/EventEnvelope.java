package com.triagemate.contracts.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


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
        @NotBlank
        @JsonProperty("eventId") String eventId,
        @NotBlank
        @JsonProperty("eventType") String eventType,     // e.g. "triagemate.ingest.input-received"


        @JsonProperty("eventVersion") int eventVersion,  // e.g. 1
        @NotNull
        @JsonProperty("occurredAt") Instant occurredAt,

        @Valid
        @JsonProperty("producer") Producer producer,
        @Valid
        @JsonProperty("trace") Trace trace,

        @NotNull
        @Valid
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
