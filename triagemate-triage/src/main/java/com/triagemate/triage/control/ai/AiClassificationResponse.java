package com.triagemate.triage.control.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for parsing the structured JSON response from the AI provider.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiClassificationResponse(
        @JsonProperty("suggestedClassification") String suggestedClassification,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("recommendsOverride") boolean recommendsOverride
) {
}
