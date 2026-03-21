package com.triagemate.triage.control.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDecisionMemoryService implements DecisionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDecisionMemoryService.class);

    private final EmbeddingService embeddingService;
    private final DecisionEmbeddingRepository embeddingRepository;

    public DefaultDecisionMemoryService(
            EmbeddingService embeddingService,
            DecisionEmbeddingRepository embeddingRepository
    ) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    @Override
    public List<DecisionExplanationContext> findSimilarDecisions(
            String query, int topK, RetrievalFilters filters) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            float[] queryVector = embeddingService.generateEmbedding(query);
            if (queryVector.length == 0) {
                return List.of();
            }

            String model = embeddingService.getModelName();
            return embeddingRepository.findSimilarWithFilters(queryVector, model, topK, filters);
        } catch (Exception e) {
            log.warn("Decision memory retrieval failed, degrading gracefully", e);
            return List.of();
        }
    }
}
