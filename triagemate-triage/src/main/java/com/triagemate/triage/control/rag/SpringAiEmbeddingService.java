package com.triagemate.triage.control.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

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
        List<Double> embedding = embeddingModel.embed(text);
        return toFloatArray(embedding);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }
}
