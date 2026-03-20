package com.triagemate.triage.control.rag;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(JdbcDecisionExplanationRepository.class)
class DecisionExplanationRepositoryIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcDecisionExplanationRepository repository;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM decision_explanations");
    }

    @Test
    void savesAndFindsById() {
        DecisionExplanation explanation = DecisionExplanation.create(
                "dec-1", "1.0.0", "basic-triage", "RULE_ERROR_KEYWORDS",
                "ACCEPT", "Error keywords detected in message",
                "Device telemetry spike context", "hash-001", 0.5
        );

        long id = repository.save(explanation);
        assertThat(id).isPositive();

        Optional<DecisionExplanation> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().decisionId()).isEqualTo("dec-1");
        assertThat(found.get().classification()).isEqualTo("RULE_ERROR_KEYWORDS");
        assertThat(found.get().outcome()).isEqualTo("ACCEPT");
        assertThat(found.get().decisionReason()).isEqualTo("Error keywords detected in message");
        assertThat(found.get().contentHash()).isEqualTo("hash-001");
        assertThat(found.get().qualityScore()).isEqualTo(0.5);
        assertThat(found.get().curatedBy()).isEqualTo("system");
        assertThat(found.get().archivedAt()).isNull();
    }

    @Test
    void existsByContentHashReturnsTrue() {
        repository.save(explanation("hash-exists"));
        assertThat(repository.existsByContentHash("hash-exists")).isTrue();
    }

    @Test
    void existsByContentHashReturnsFalse() {
        assertThat(repository.existsByContentHash("nonexistent-hash")).isFalse();
    }

    @Test
    void findByClassificationReturnsMatches() {
        repository.save(explanationWithClassification("RULE_A", "hash-a1"));
        repository.save(explanationWithClassification("RULE_A", "hash-a2"));
        repository.save(explanationWithClassification("RULE_B", "hash-b1"));

        List<DecisionExplanation> results = repository.findByClassification("RULE_A");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> "RULE_A".equals(e.classification()));
    }

    @Test
    void uniqueConstraintPreventsActiveDuplicates() {
        repository.save(explanation("duplicate-hash"));

        assertThatThrownBy(() -> repository.save(explanation("duplicate-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsArchivedDuplicates() {
        // Save first, then archive it
        long id = repository.save(explanation("archivable-hash"));
        jdbcTemplate.update("UPDATE decision_explanations SET archived_at = NOW() WHERE id = ?", id);

        // Save another with the same hash — should succeed because the first is archived
        long id2 = repository.save(explanation("archivable-hash"));
        assertThat(id2).isPositive();
        assertThat(id2).isNotEqualTo(id);
    }

    // --- helpers ---

    private DecisionExplanation explanation(String contentHash) {
        return DecisionExplanation.create(
                "dec-test", "1.0.0", "basic-triage", "RULE_TEST",
                "ACCEPT", "Test reason for explanation",
                "Test context summary", contentHash, 0.5
        );
    }

    private DecisionExplanation explanationWithClassification(String classification, String contentHash) {
        return DecisionExplanation.create(
                "dec-test", "1.0.0", "basic-triage", classification,
                "ACCEPT", "Test reason for " + classification,
                "Test context summary", contentHash, 0.5
        );
    }
}
