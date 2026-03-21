package com.triagemate.triage.control.rag;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({JdbcDecisionEmbeddingRepository.class, JdbcDecisionExplanationRepository.class})
class EmbeddingReindexIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcDecisionEmbeddingRepository embeddingRepository;

    @Autowired
    private JdbcDecisionExplanationRepository explanationRepository;

    private EmbeddingReindexService service;

    private final StubEmbeddingService embeddingService = new StubEmbeddingService("nomic-embed-text");

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM decision_embeddings");
        jdbcTemplate.update("DELETE FROM decision_explanations");

        service = new EmbeddingReindexService(
                embeddingService, new EmbeddingTextPreparer(),
                explanationRepository, embeddingRepository
        );
    }

    @Test
    void reindexCreatesEmbeddingsForAllExplanations() {
        insertExplanation("dec-001", "RULE_A", "Reason A", "hash-001");
        insertExplanation("dec-002", "RULE_B", "Reason B", "hash-002");

        ReindexResult result = service.reindex();

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.model()).isEqualTo("nomic-embed-text");
    }

    @Test
    void reindexIsIdempotent() {
        insertExplanation("dec-001", "RULE_A", "Reason A", "hash-001");

        ReindexResult first = service.reindex();
        assertThat(first.created()).isEqualTo(1);

        ReindexResult second = service.reindex();
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
    }

    @Test
    void purgeRemovesOldModelKeepsCurrent() {
        long expId = insertExplanation("dec-001", "RULE_A", "Reason A", "hash-001");

        // Create embeddings for two different models
        embeddingRepository.save(DecisionEmbedding.create(expId, generateVector(768, 0.1f), "nomic-embed-text"));
        embeddingRepository.save(DecisionEmbedding.create(expId, generateVector(768, 0.2f), "old-model"));

        int deleted = service.purgeOldModelEmbeddings("nomic-embed-text");

        assertThat(deleted).isEqualTo(1);
        assertThat(embeddingRepository.countByModel("nomic-embed-text")).isEqualTo(1);
        assertThat(embeddingRepository.countByModel("old-model")).isZero();
    }

    @Test
    void retrievalOnlyReturnsCurrentModelAfterReindexAndPurge() {
        long expId = insertExplanation("dec-001", "RULE_A", "Reason A", "hash-001");

        // Simulate old model embeddings
        embeddingRepository.save(DecisionEmbedding.create(expId, generateVector(768, 0.5f), "old-model"));

        // Reindex with current model
        service.reindex();

        // Purge old
        service.purgeOldModelEmbeddings("nomic-embed-text");

        // Retrieval for current model returns results
        List<DecisionEmbedding> current = embeddingRepository.findSimilar(
                generateVector(768, 0.5f), "nomic-embed-text", 10);
        assertThat(current).hasSize(1);

        // Retrieval for old model returns nothing
        List<DecisionEmbedding> old = embeddingRepository.findSimilar(
                generateVector(768, 0.5f), "old-model", 10);
        assertThat(old).isEmpty();
    }

    @Test
    void isReindexNeededDetectsModelDrift() {
        insertExplanation("dec-001", "RULE_A", "Reason A", "hash-001");

        assertThat(service.isReindexNeeded()).isTrue();

        service.reindex();

        assertThat(service.isReindexNeeded()).isFalse();
    }

    private long insertExplanation(String decisionId, String classification, String reason, String hash) {
        return explanationRepository.save(DecisionExplanation.create(
                decisionId, "1.0.0", "basic-triage",
                classification, "ACCEPT", reason,
                "Context summary", hash, 0.8
        ));
    }

    private static float[] generateVector(int dimension, float baseValue) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = baseValue + (i * 0.0001f);
        }
        return vector;
    }

    /**
     * Stub embedding service that returns deterministic vectors for testing.
     */
    private static class StubEmbeddingService implements EmbeddingService {
        private final String modelName;

        StubEmbeddingService(String modelName) {
            this.modelName = modelName;
        }

        @Override
        public float[] generateEmbedding(String text) {
            // Generate a deterministic vector based on text hash
            float base = Math.abs(text.hashCode() % 100) / 100.0f;
            float[] vector = new float[768];
            for (int i = 0; i < 768; i++) {
                vector[i] = base + (i * 0.0001f);
            }
            return vector;
        }

        @Override
        public String getModelName() {
            return modelName;
        }
    }
}
