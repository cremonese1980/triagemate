package com.triagemate.triage.control.rag;

import org.springframework.ai.embedding.EmbeddingModel;

public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public SpringAiEmbeddingService(EmbeddingModel embeddingModel, String modelName) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        return embeddingModel.embed(text);
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
