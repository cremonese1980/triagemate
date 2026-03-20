package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringAiEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private SpringAiEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        service = new SpringAiEmbeddingService(embeddingModel, "nomic-embed-text");
    }

    @Test
    void generatesEmbedding() {
        when(embeddingModel.embed("test text"))
                .thenReturn(List.of(0.1, 0.2, 0.3));

        float[] result = service.generateEmbedding("test text");

        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
        assertEquals(0.2f, result[1], 0.001f);
        assertEquals(0.3f, result[2], 0.001f);
        verify(embeddingModel).embed("test text");
    }

    @Test
    void returnsModelName() {
        assertEquals("nomic-embed-text", service.getModelName());
    }

    @Test
    void handlesEmptyText() {
        float[] result = service.generateEmbedding("");
        assertEquals(0, result.length);
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void handlesNullText() {
        float[] result = service.generateEmbedding(null);
        assertEquals(0, result.length);
        verifyNoInteractions(embeddingModel);
    }
}
