package com.triagemate.triage.control.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcDecisionExplanationRepository implements DecisionExplanationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDecisionExplanationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long save(DecisionExplanation explanation) {
        String sql = """
                INSERT INTO decision_explanations
                    (decision_id, policy_version, policy_family, classification, outcome,
                     decision_reason, decision_context_summary, content_hash,
                     quality_score, curated_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                explanation.decisionId(),
                explanation.policyVersion(),
                explanation.policyFamily(),
                explanation.classification(),
                explanation.outcome(),
                explanation.decisionReason(),
                explanation.decisionContextSummary(),
                explanation.contentHash(),
                explanation.qualityScore(),
                explanation.curatedBy(),
                Timestamp.from(explanation.createdAt())
        );

        return id != null ? id : -1;
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        String sql = "SELECT COUNT(*) FROM decision_explanations WHERE content_hash = ? AND archived_at IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, contentHash);
        return count != null && count > 0;
    }

    @Override
    public Optional<DecisionExplanation> findById(long id) {
        String sql = "SELECT * FROM decision_explanations WHERE id = ?";
        List<DecisionExplanation> results = jdbcTemplate.query(sql, MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<DecisionExplanation> findByClassification(String classification) {
        String sql = "SELECT * FROM decision_explanations WHERE classification = ? AND archived_at IS NULL ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, MAPPER, classification);
    }

    @Override
    public List<DecisionExplanation> findAllNonArchived() {
        String sql = "SELECT * FROM decision_explanations WHERE archived_at IS NULL ORDER BY id";
        return jdbcTemplate.query(sql, MAPPER);
    }

    @Override
    public int countNonArchived() {
        String sql = "SELECT COUNT(*) FROM decision_explanations WHERE archived_at IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    private static final RowMapper<DecisionExplanation> MAPPER = (ResultSet rs, int rowNum) -> {
        Timestamp archivedTs = rs.getTimestamp("archived_at");
        return new DecisionExplanation(
                rs.getLong("id"),
                rs.getString("decision_id"),
                rs.getString("policy_version"),
                rs.getString("policy_family"),
                rs.getString("classification"),
                rs.getString("outcome"),
                rs.getString("decision_reason"),
                rs.getString("decision_context_summary"),
                rs.getString("content_hash"),
                rs.getDouble("quality_score"),
                rs.getString("curated_by"),
                rs.getTimestamp("created_at").toInstant(),
                archivedTs != null ? archivedTs.toInstant() : null
        );
    };
}
