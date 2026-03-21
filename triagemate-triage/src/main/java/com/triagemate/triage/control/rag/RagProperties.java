package com.triagemate.triage.control.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "triagemate.rag")
public record RagProperties(
        boolean enabled,
        String embeddingModel,
        int embeddingDimension,
        int maxContextSummaryLength,
        double defaultQualityScore,
        Retrieval retrieval
) {

    public record Retrieval(int topK, double minQualityScore, String minPolicyVersion) {
        public Retrieval {
            if (topK <= 0) topK = 3;
            if (minQualityScore <= 0.0 || minQualityScore > 1.0) minQualityScore = 0.5;
        }
    }

    public RagProperties {
        embeddingModel = Objects.requireNonNullElse(embeddingModel, "nomic-embed-text");
        if (embeddingDimension <= 0) embeddingDimension = 768;
        if (maxContextSummaryLength <= 0) maxContextSummaryLength = 500;
        if (defaultQualityScore <= 0.0 || defaultQualityScore > 1.0) defaultQualityScore = 0.5;
        if (retrieval == null) retrieval = new Retrieval(3, 0.5, null);
    }
}
