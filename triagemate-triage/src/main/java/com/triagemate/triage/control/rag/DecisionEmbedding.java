package com.triagemate.triage.control.rag;

import java.time.Instant;

public record DecisionEmbedding(
        Long id,
        long decisionExplanationId,
        float[] embedding,
        String embeddingModel,
        Instant createdAt
) {

    public static DecisionEmbedding create(
            long decisionExplanationId,
            float[] embedding,
            String embeddingModel
    ) {
        return new DecisionEmbedding(
                null, decisionExplanationId, embedding, embeddingModel, Instant.now()
        );
    }
}
