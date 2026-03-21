package com.triagemate.triage.control.rag;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import({JdbcDecisionEmbeddingRepository.class, JdbcDecisionExplanationRepository.class})
class DecisionEmbeddingRepositoryIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcDecisionEmbeddingRepository repository;

    @Autowired
    private JdbcDecisionExplanationRepository explanationRepository;

    private long explanationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM decision_embeddings");
        jdbcTemplate.update("DELETE FROM decision_explanations");

        DecisionExplanation explanation = DecisionExplanation.create(
                "decision-001", "1.0.0", "basic-triage",
                "RULE_ERROR_KEYWORDS", "ACCEPT",
                "Device telemetry spike detected",
                "Sensor anomaly in zone A",
                "hash-exp-001", 0.8
        );
        explanationId = explanationRepository.save(explanation);
    }

    @Test
    void savesAndFindsByExplanationId() {
        float[] vector = generateVector(768, 0.1f);
        DecisionEmbedding embedding = DecisionEmbedding.create(
                explanationId, vector, "nomic-embed-text"
        );

        long id = repository.save(embedding);
        assertThat(id).isPositive();

        Optional<DecisionEmbedding> found = repository.findByExplanationId(explanationId);
        assertThat(found).isPresent();
        assertThat(found.get().decisionExplanationId()).isEqualTo(explanationId);
        assertThat(found.get().embeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(found.get().embedding()).hasSize(768);
    }

    @Test
    void findByExplanationIdReturnsEmptyWhenNotFound() {
        Optional<DecisionEmbedding> found = repository.findByExplanationId(99999L);
        assertThat(found).isEmpty();
    }

    @Test
    void existsByExplanationIdReturnsTrueWhenExists() {
        float[] vector = generateVector(768, 0.5f);
        repository.save(DecisionEmbedding.create(explanationId, vector, "nomic-embed-text"));

        assertThat(repository.existsByExplanationId(explanationId)).isTrue();
    }

    @Test
    void existsByExplanationIdReturnsFalseWhenNotExists() {
        assertThat(repository.existsByExplanationId(99999L)).isFalse();
    }

    @Test
    void findSimilarReturnsByDistance() {
        // Create a second explanation for a second embedding
        long explanationId2 = explanationRepository.save(DecisionExplanation.create(
                "decision-002", "1.0.0", "basic-triage",
                "RULE_URGENT_EMAIL", "ACCEPT",
                "Urgent escalation required",
                "Priority alert",
                "hash-exp-002", 0.9
        ));

        // Insert two embeddings with known vectors
        float[] vectorA = generateVector(768, 0.1f);
        float[] vectorB = generateVector(768, 0.9f);
        repository.save(DecisionEmbedding.create(explanationId, vectorA, "nomic-embed-text"));
        repository.save(DecisionEmbedding.create(explanationId2, vectorB, "nomic-embed-text"));

        // Query with a vector close to vectorA
        float[] query = generateVector(768, 0.11f);
        List<DecisionEmbedding> results = repository.findSimilar(query, "nomic-embed-text", 2);

        assertThat(results).hasSize(2);
        // First result should be closer to query (vectorA at 0.1 is closer to 0.11 than vectorB at 0.9)
        assertThat(results.get(0).decisionExplanationId()).isEqualTo(explanationId);
    }

    @Test
    void findSimilarFiltersByModel() {
        repository.save(DecisionEmbedding.create(
                explanationId, generateVector(768, 0.5f), "model-other"
        ));

        float[] query = generateVector(768, 0.5f);
        List<DecisionEmbedding> results = repository.findSimilar(query, "nomic-embed-text", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void findSimilarRespectsLimit() {
        long explanationId2 = explanationRepository.save(DecisionExplanation.create(
                "decision-003", "1.0.0", "basic-triage",
                "RULE_OTHER", "REJECT",
                "Rejected for policy violation",
                "Context",
                "hash-exp-003", 0.7
        ));

        repository.save(DecisionEmbedding.create(explanationId, generateVector(768, 0.1f), "nomic-embed-text"));
        repository.save(DecisionEmbedding.create(explanationId2, generateVector(768, 0.9f), "nomic-embed-text"));

        float[] query = generateVector(768, 0.5f);
        List<DecisionEmbedding> results = repository.findSimilar(query, "nomic-embed-text", 1);

        assertThat(results).hasSize(1);
    }

    private static float[] generateVector(int dimension, float baseValue) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = baseValue + (i * 0.0001f);
        }
        return vector;
    }
}
