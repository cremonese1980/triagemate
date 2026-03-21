package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbeddingReindexServiceTest {

    private EmbeddingService embeddingService;
    private EmbeddingTextPreparer textPreparer;
    private DecisionExplanationRepository explanationRepository;
    private DecisionEmbeddingRepository embeddingRepository;
    private EmbeddingReindexService service;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        textPreparer = new EmbeddingTextPreparer();
        explanationRepository = mock(DecisionExplanationRepository.class);
        embeddingRepository = mock(DecisionEmbeddingRepository.class);
        service = new EmbeddingReindexService(embeddingService, textPreparer, explanationRepository, embeddingRepository);

        when(embeddingService.getModelName()).thenReturn("nomic-embed-text");
    }

    @Test
    void reindexCreatesEmbeddingsForUnindexedExplanations() {
        DecisionExplanation exp = new DecisionExplanation(
                1L, "dec-001", "1.0.0", "basic-triage",
                "RULE_ERROR", "ACCEPT", "Device error detected",
                "Context", "hash-001", 0.8, "system", Instant.now(), null
        );
        when(explanationRepository.findAllNonArchived()).thenReturn(List.of(exp));
        when(embeddingRepository.existsByExplanationIdAndModel(1L, "nomic-embed-text")).thenReturn(false);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        ReindexResult result = service.reindex();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.model()).isEqualTo("nomic-embed-text");
        verify(embeddingRepository).save(any(DecisionEmbedding.class));
    }

    @Test
    void reindexSkipsAlreadyIndexedExplanations() {
        DecisionExplanation exp = new DecisionExplanation(
                1L, "dec-001", "1.0.0", "basic-triage",
                "RULE_ERROR", "ACCEPT", "Device error detected",
                "Context", "hash-001", 0.8, "system", Instant.now(), null
        );
        when(explanationRepository.findAllNonArchived()).thenReturn(List.of(exp));
        when(embeddingRepository.existsByExplanationIdAndModel(1L, "nomic-embed-text")).thenReturn(true);

        ReindexResult result = service.reindex();

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void reindexHandlesIndividualFailuresGracefully() {
        DecisionExplanation exp1 = new DecisionExplanation(
                1L, "dec-001", "1.0.0", "basic-triage",
                "RULE_A", "ACCEPT", "Reason A",
                null, "hash-001", 0.8, "system", Instant.now(), null
        );
        DecisionExplanation exp2 = new DecisionExplanation(
                2L, "dec-002", "1.0.0", "basic-triage",
                "RULE_B", "ACCEPT", "Reason B",
                null, "hash-002", 0.8, "system", Instant.now(), null
        );
        when(explanationRepository.findAllNonArchived()).thenReturn(List.of(exp1, exp2));
        when(embeddingRepository.existsByExplanationIdAndModel(anyLong(), any())).thenReturn(false);
        when(embeddingService.generateEmbedding(anyString()))
                .thenThrow(new RuntimeException("API error"))
                .thenReturn(new float[]{0.1f});

        ReindexResult result = service.reindex();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void reindexCountsEmptyEmbeddingsAsFailed() {
        DecisionExplanation exp = new DecisionExplanation(
                1L, "dec-001", "1.0.0", "basic-triage",
                "RULE_A", "ACCEPT", "Reason",
                null, "hash-001", 0.8, "system", Instant.now(), null
        );
        when(explanationRepository.findAllNonArchived()).thenReturn(List.of(exp));
        when(embeddingRepository.existsByExplanationIdAndModel(1L, "nomic-embed-text")).thenReturn(false);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[0]);

        ReindexResult result = service.reindex();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isZero();
        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void purgeOldModelEmbeddingsDelegatesToRepository() {
        when(embeddingRepository.deleteByModelNot("nomic-embed-text")).thenReturn(5);

        int deleted = service.purgeOldModelEmbeddings("nomic-embed-text");

        assertThat(deleted).isEqualTo(5);
        verify(embeddingRepository).deleteByModelNot("nomic-embed-text");
    }

    @Test
    void isReindexNeededReturnsTrueWhenNoCurrentModelEmbeddings() {
        when(embeddingRepository.countByModel("nomic-embed-text")).thenReturn(0);
        when(explanationRepository.countNonArchived()).thenReturn(10);

        assertThat(service.isReindexNeeded()).isTrue();
    }

    @Test
    void isReindexNeededReturnsFalseWhenFullyIndexed() {
        when(embeddingRepository.countByModel("nomic-embed-text")).thenReturn(10);
        when(explanationRepository.countNonArchived()).thenReturn(10);

        assertThat(service.isReindexNeeded()).isFalse();
    }

    @Test
    void isReindexNeededReturnsFalseWhenNoExplanations() {
        when(embeddingRepository.countByModel("nomic-embed-text")).thenReturn(0);
        when(explanationRepository.countNonArchived()).thenReturn(0);

        assertThat(service.isReindexNeeded()).isFalse();
    }
}
