package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultDecisionMemoryServiceTest {

    private EmbeddingService embeddingService;
    private DecisionEmbeddingRepository embeddingRepository;
    private DefaultDecisionMemoryService service;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        embeddingRepository = mock(DecisionEmbeddingRepository.class);
        service = new DefaultDecisionMemoryService(embeddingService, embeddingRepository);

        when(embeddingService.getModelName()).thenReturn("nomic-embed-text");
    }

    @Test
    void findsSimlarDecisionsSuccessfully() {
        float[] queryVector = {0.1f, 0.2f, 0.3f};
        when(embeddingService.generateEmbedding("device error")).thenReturn(queryVector);

        DecisionExplanationContext ctx = new DecisionExplanationContext(
                1L, "RULE_ERROR_KEYWORDS", "ACCEPT",
                "Device telemetry spike", "Sensor anomaly",
                "1.0.0", "basic-triage", 0.8, 0.12
        );
        RetrievalFilters filters = RetrievalFilters.withDefaults("basic-triage");
        when(embeddingRepository.findSimilarWithFilters(queryVector, "nomic-embed-text", 3, filters))
                .thenReturn(List.of(ctx));

        List<DecisionExplanationContext> results = service.findSimilarDecisions("device error", 3, filters);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().classification()).isEqualTo("RULE_ERROR_KEYWORDS");
        verify(embeddingService).generateEmbedding("device error");
        verify(embeddingRepository).findSimilarWithFilters(queryVector, "nomic-embed-text", 3, filters);
    }

    @Test
    void returnsEmptyForNullQuery() {
        List<DecisionExplanationContext> results = service.findSimilarDecisions(
                null, 3, RetrievalFilters.withDefaults("basic-triage"));

        assertThat(results).isEmpty();
        verifyNoInteractions(embeddingService, embeddingRepository);
    }

    @Test
    void returnsEmptyForBlankQuery() {
        List<DecisionExplanationContext> results = service.findSimilarDecisions(
                "   ", 3, RetrievalFilters.withDefaults("basic-triage"));

        assertThat(results).isEmpty();
        verifyNoInteractions(embeddingService, embeddingRepository);
    }

    @Test
    void returnsEmptyWhenEmbeddingIsEmpty() {
        when(embeddingService.generateEmbedding("test")).thenReturn(new float[0]);

        List<DecisionExplanationContext> results = service.findSimilarDecisions(
                "test", 3, RetrievalFilters.withDefaults("basic-triage"));

        assertThat(results).isEmpty();
        verify(embeddingRepository, never()).findSimilarWithFilters(any(), any(), anyInt(), any());
    }

    @Test
    void degradesGracefullyOnEmbeddingFailure() {
        when(embeddingService.generateEmbedding("test"))
                .thenThrow(new RuntimeException("Embedding service unavailable"));

        List<DecisionExplanationContext> results = service.findSimilarDecisions(
                "test", 3, RetrievalFilters.withDefaults("basic-triage"));

        assertThat(results).isEmpty();
    }

    @Test
    void degradesGracefullyOnRepositoryFailure() {
        when(embeddingService.generateEmbedding("test")).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingRepository.findSimilarWithFilters(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Database unavailable"));

        List<DecisionExplanationContext> results = service.findSimilarDecisions(
                "test", 3, RetrievalFilters.withDefaults("basic-triage"));

        assertThat(results).isEmpty();
    }
}
