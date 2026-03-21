package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
        DecisionExplanation exp = explanation(1L, "RULE_ERROR", "Device error detected");
        stubBatch(List.of(exp));
        when(embeddingRepository.findIndexedExplanationIds(List.of(1L), "nomic-embed-text"))
                .thenReturn(Set.of());
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
        DecisionExplanation exp = explanation(1L, "RULE_ERROR", "Device error detected");
        stubBatch(List.of(exp));
        when(embeddingRepository.findIndexedExplanationIds(List.of(1L), "nomic-embed-text"))
                .thenReturn(Set.of(1L));

        ReindexResult result = service.reindex();

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void reindexHandlesIndividualFailuresGracefully() {
        DecisionExplanation exp1 = explanation(1L, "RULE_A", "Reason A");
        DecisionExplanation exp2 = explanation(2L, "RULE_B", "Reason B");
        stubBatch(List.of(exp1, exp2));
        when(embeddingRepository.findIndexedExplanationIds(anyList(), any()))
                .thenReturn(Set.of());
        when(embeddingService.generateEmbedding(anyString()))
                .thenThrow(new RuntimeException("API error"))
                .thenReturn(new float[]{0.1f});

        ReindexResult result = service.reindex();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void reindexCountsEmptyEmbeddingsAsFailed() {
        DecisionExplanation exp = explanation(1L, "RULE_A", "Reason");
        stubBatch(List.of(exp));
        when(embeddingRepository.findIndexedExplanationIds(List.of(1L), "nomic-embed-text"))
                .thenReturn(Set.of());
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

    // --- helpers ---

    private DecisionExplanation explanation(long id, String classification, String reason) {
        return new DecisionExplanation(
                id, "dec-" + id, "1.0.0", "basic-triage",
                classification, "ACCEPT", reason,
                null, "hash-" + id, 0.8, "system", Instant.now(), null
        );
    }

    private void stubBatch(List<DecisionExplanation> explanations) {
        when(explanationRepository.countNonArchived()).thenReturn(explanations.size());
        when(explanationRepository.findNonArchivedBatch(eq(0L), anyInt())).thenReturn(explanations);
        when(explanationRepository.findNonArchivedBatch(
                eq(explanations.getLast().id()), anyInt())).thenReturn(List.of());
    }
}
