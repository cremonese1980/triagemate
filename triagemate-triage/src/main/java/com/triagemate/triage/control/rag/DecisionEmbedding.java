package com.triagemate.triage.control.rag;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class DecisionEmbedding {

    private final Long id;
    private final long decisionExplanationId;
    private final float[] embedding;
    private final String embeddingModel;
    private final Instant createdAt;

    public DecisionEmbedding(
            Long id,
            long decisionExplanationId,
            float[] embedding,
            String embeddingModel,
            Instant createdAt
    ) {
        this.id = id;
        this.decisionExplanationId = decisionExplanationId;
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.createdAt = createdAt;
    }

    public static DecisionEmbedding create(
            long decisionExplanationId,
            float[] embedding,
            String embeddingModel
    ) {
        return new DecisionEmbedding(
                null, decisionExplanationId, embedding, embeddingModel, Instant.now()
        );
    }

    public Long id() { return id; }
    public long decisionExplanationId() { return decisionExplanationId; }
    public float[] embedding() { return embedding; }
    public String embeddingModel() { return embeddingModel; }
    public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecisionEmbedding that)) return false;
        return decisionExplanationId == that.decisionExplanationId
                && Arrays.equals(embedding, that.embedding)
                && Objects.equals(id, that.id)
                && Objects.equals(embeddingModel, that.embeddingModel)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, decisionExplanationId, embeddingModel, createdAt);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }

    @Override
    public String toString() {
        return "DecisionEmbedding[id=" + id
                + ", decisionExplanationId=" + decisionExplanationId
                + ", embeddingModel=" + embeddingModel
                + ", dimension=" + (embedding != null ? embedding.length : 0)
                + ", createdAt=" + createdAt + "]";
    }
}
