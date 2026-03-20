package com.triagemate.triage.control.rag;

public interface EmbeddingService {

    float[] generateEmbedding(String text);

    String getModelName();
}
