package com.triagemate.triage.control.rag;

import java.time.Instant;

public record EmbeddingCacheEntry(
        Long id,
        String contentHash,
        float[] embeddingVector,
        String embeddingModel,
        int dimension,
        Instant createdAt,
        Instant lastAccessedAt,
        int accessCount
) {

    public static EmbeddingCacheEntry create(
            String contentHash,
            float[] embeddingVector,
            String embeddingModel,
            int dimension
    ) {
        return new EmbeddingCacheEntry(
                null, contentHash, embeddingVector, embeddingModel,
                dimension, Instant.now(), Instant.now(), 1
        );
    }
}
