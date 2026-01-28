package com.gabriele.triagemate.contracts.events.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutcomeRecordedV1(
        @JsonProperty("decisionId") String decisionId,
        @JsonProperty("inputId") String inputId,
        @JsonProperty("outcome") String outcome,      // "accepted" | "rejected" | "overridden" | "unknown"
        @JsonProperty("notes") String notes
) {}
