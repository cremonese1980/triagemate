package com.triagemate.triage.control.rag;

import java.util.List;
import java.util.Optional;

public interface DecisionEmbeddingRepository {

    long save(DecisionEmbedding embedding);

    Optional<DecisionEmbedding> findByExplanationId(long explanationId);

    List<DecisionEmbedding> findSimilar(float[] queryVector, String embeddingModel, int limit);

    boolean existsByExplanationId(long explanationId);
}
