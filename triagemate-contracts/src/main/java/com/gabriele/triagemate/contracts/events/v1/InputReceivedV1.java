package com.gabriele.triagemate.contracts.events.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InputReceivedV1(
        @JsonProperty("inputId") String inputId,   // stable id for the ingested unit
        @JsonProperty("channel") String channel,   // "email" | "ticket" | "message" | ...
        @JsonProperty("subject") String subject,
        @JsonProperty("text") String text,
        @JsonProperty("from") String from,
        @JsonProperty("receivedAtEpochMs") long receivedAtEpochMs
) {}
