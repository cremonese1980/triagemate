package com.triagemate.contracts.events.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DecisionMadeV1(
        @JsonProperty("decisionId") String decisionId,
        @JsonProperty("inputId") String inputId,

        @JsonProperty("priority") String priority,           // "P0".."P3"
        @JsonProperty("categories") List<String> categories, // e.g. ["billing","bug"]
        @JsonProperty("summary") String summary,             // short, operational
        @JsonProperty("suggestedActions") List<String> suggestedActions,

        @JsonProperty("motivation") Motivation motivation
) {
    public record Motivation(
            @JsonProperty("type") String type,           // "rules" | "ai" | "hybrid"
            @JsonProperty("reasons") List<String> reasons // bullet reasons, human-readable
    ) {}
}
