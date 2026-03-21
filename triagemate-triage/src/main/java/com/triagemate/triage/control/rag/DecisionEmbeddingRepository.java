package com.triagemate.triage.control.rag;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DecisionEmbeddingRepository {

    long save(DecisionEmbedding embedding);

    Optional<DecisionEmbedding> findByExplanationId(long explanationId);

    List<DecisionEmbedding> findSimilar(float[] queryVector, String embeddingModel, int limit);

    boolean existsByExplanationId(long explanationId);

    List<DecisionExplanationContext> findSimilarWithFilters(
            float[] queryVector, String embeddingModel, int limit, RetrievalFilters filters);

    boolean existsByExplanationIdAndModel(long explanationId, String embeddingModel);

    Set<Long> findIndexedExplanationIds(List<Long> explanationIds, String embeddingModel);

    int deleteByModelNot(String embeddingModel);

    int countByModel(String embeddingModel);
}
