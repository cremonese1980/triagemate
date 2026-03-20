package com.triagemate.triage.control.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "triagemate.rag")
public record RagProperties(
        boolean enabled,
        String embeddingModel,
        int embeddingDimension,
        int maxContextSummaryLength,
        double defaultQualityScore
) {
    public RagProperties {
        embeddingModel = Objects.requireNonNullElse(embeddingModel, "nomic-embed-text");
        if (embeddingDimension <= 0) embeddingDimension = 768;
        if (maxContextSummaryLength <= 0) maxContextSummaryLength = 500;
        if (defaultQualityScore <= 0.0 || defaultQualityScore > 1.0) defaultQualityScore = 0.5;
    }
}
