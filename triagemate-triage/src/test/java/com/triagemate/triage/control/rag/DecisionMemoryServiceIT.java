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
class DecisionMemoryServiceIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcDecisionEmbeddingRepository embeddingRepository;

    @Autowired
    private JdbcDecisionExplanationRepository explanationRepository;

    private long expId1;
    private long expId2;
    private long expId3;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM decision_embeddings");
        jdbcTemplate.update("DELETE FROM decision_explanations");

        expId1 = explanationRepository.save(DecisionExplanation.create(
                "dec-001", "1.0.0", "basic-triage",
                "RULE_ERROR_KEYWORDS", "ACCEPT",
                "Device telemetry spike detected",
                "Sensor anomaly in zone A",
                "hash-001", 0.8
        ));

        expId2 = explanationRepository.save(DecisionExplanation.create(
                "dec-002", "2.0.0", "basic-triage",
                "RULE_URGENT_EMAIL", "ACCEPT",
                "Urgent escalation required",
                "Priority alert from monitoring",
                "hash-002", 0.9
        ));

        expId3 = explanationRepository.save(DecisionExplanation.create(
                "dec-003", "2.0.0", "basic-triage",
                "RULE_ERROR_KEYWORDS", "REJECT",
                "Low quality noise signal",
                "Background noise",
                "hash-003", 0.3
        ));

        // Insert embeddings — vectors designed so we know cosine distance ordering
        embeddingRepository.save(DecisionEmbedding.create(expId1, generateVector(768, 0.1f), "nomic-embed-text"));
        embeddingRepository.save(DecisionEmbedding.create(expId2, generateVector(768, 0.5f), "nomic-embed-text"));
        embeddingRepository.save(DecisionEmbedding.create(expId3, generateVector(768, 0.9f), "nomic-embed-text"));
    }

    @Test
    void findSimilarWithFiltersReturnsByDistance() {
        float[] query = generateVector(768, 0.11f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 3, filters);

        assertThat(results).hasSize(3);
        // Closest to 0.11 is vector at 0.1, then 0.5, then 0.9
        assertThat(results.get(0).explanationId()).isEqualTo(expId1);
        assertThat(results.get(0).classification()).isEqualTo("RULE_ERROR_KEYWORDS");
        assertThat(results.get(0).similarityScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void filtersByPolicyVersion() {
        float[] query = generateVector(768, 0.11f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, null, "2.0.0");

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        // Only expId2 and expId3 have policy_version "2.0.0"
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(ctx ->
                assertThat(ctx.policyVersion()).isGreaterThanOrEqualTo("2.0.0")
        );
    }

    @Test
    void filtersByClassification() {
        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(
                List.of("RULE_URGENT_EMAIL"), null, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().classification()).isEqualTo("RULE_URGENT_EMAIL");
    }

    @Test
    void filtersByQualityScore() {
        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), 0.7, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        // expId3 has quality 0.3 — excluded
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(ctx ->
                assertThat(ctx.qualityScore()).isGreaterThanOrEqualTo(0.7)
        );
    }

    @Test
    void filtersByPolicyFamily() {
        // Add an explanation with different policy family
        long expId4 = explanationRepository.save(DecisionExplanation.create(
                "dec-004", "1.0.0", "advanced-triage",
                "RULE_OTHER", "ACCEPT",
                "Advanced rule match",
                "Different family",
                "hash-004", 0.8
        ));
        embeddingRepository.save(DecisionEmbedding.create(expId4, generateVector(768, 0.5f), "nomic-embed-text"));

        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, "basic-triage", null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        assertThat(results).hasSize(3);
        assertThat(results).allSatisfy(ctx ->
                assertThat(ctx.policyFamily()).isEqualTo("basic-triage")
        );
    }

    @Test
    void filtersByEmbeddingModel() {
        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "other-model", 10, filters);

        assertThat(results).isEmpty();
    }

    @Test
    void combinedFilters() {
        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(
                List.of("RULE_ERROR_KEYWORDS"), 0.5, "basic-triage", "2.0.0");

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        // Only expId3: RULE_ERROR_KEYWORDS + version 2.0.0 — but quality 0.3 < 0.5, so excluded
        assertThat(results).isEmpty();
    }

    @Test
    void excludesArchivedExplanations() {
        // Archive expId1
        jdbcTemplate.update("UPDATE decision_explanations SET archived_at = NOW() WHERE id = ?", expId1);

        float[] query = generateVector(768, 0.11f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 10, filters);

        assertThat(results).hasSize(2);
        assertThat(results).noneMatch(ctx -> ctx.explanationId() == expId1);
    }

    @Test
    void respectsLimit() {
        float[] query = generateVector(768, 0.5f);
        RetrievalFilters filters = new RetrievalFilters(List.of(), null, null, null);

        List<DecisionExplanationContext> results =
                embeddingRepository.findSimilarWithFilters(query, "nomic-embed-text", 1, filters);

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
