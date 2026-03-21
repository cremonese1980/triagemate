package com.triagemate.triage.control.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JdbcDecisionEmbeddingRepository implements DecisionEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDecisionEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long save(DecisionEmbedding embedding) {
        String sql = """
                INSERT INTO decision_embeddings
                    (decision_explanation_id, embedding, embedding_model, created_at)
                VALUES (?, ?::vector, ?, NOW())
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                embedding.decisionExplanationId(),
                toVectorLiteral(embedding.embedding()),
                embedding.embeddingModel()
        );

        return id != null ? id : -1;
    }

    @Override
    public Optional<DecisionEmbedding> findByExplanationId(long explanationId) {
        String sql = "SELECT * FROM decision_embeddings WHERE decision_explanation_id = ?";
        List<DecisionEmbedding> results = jdbcTemplate.query(sql, MAPPER, explanationId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<DecisionEmbedding> findSimilar(float[] queryVector, String embeddingModel, int limit) {
        String sql = """
                SELECT * FROM decision_embeddings
                WHERE embedding_model = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, MAPPER,
                embeddingModel,
                toVectorLiteral(queryVector),
                limit
        );
    }

    @Override
    public boolean existsByExplanationId(long explanationId) {
        String sql = "SELECT COUNT(*) FROM decision_embeddings WHERE decision_explanation_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, explanationId);
        return count != null && count > 0;
    }

    @Override
    public List<DecisionExplanationContext> findSimilarWithFilters(
            float[] queryVector, String embeddingModel, int limit, RetrievalFilters filters) {

        String vectorLiteral = toVectorLiteral(queryVector);
        List<Object> params = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
                SELECT dx.id AS explanation_id, dx.classification, dx.outcome,
                       dx.decision_reason, dx.decision_context_summary,
                       dx.policy_version, dx.policy_family, dx.quality_score,
                       (de.embedding <=> ?::vector) AS similarity_score
                FROM decision_embeddings de
                JOIN decision_explanations dx ON dx.id = de.decision_explanation_id
                WHERE de.embedding_model = ?
                  AND dx.archived_at IS NULL
                """);
        params.add(vectorLiteral);
        params.add(embeddingModel);

        if (filters != null) {
            if (filters.policyFamily() != null && !filters.policyFamily().isBlank()) {
                sql.append("  AND dx.policy_family = ?\n");
                params.add(filters.policyFamily());
            }
            if (filters.minPolicyVersion() != null && !filters.minPolicyVersion().isBlank()) {
                sql.append("  AND dx.policy_version >= ?\n");
                params.add(filters.minPolicyVersion());
            }
            if (filters.classifications() != null && !filters.classifications().isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(filters.classifications().size(), "?"));
                sql.append("  AND dx.classification IN (").append(placeholders).append(")\n");
                params.addAll(filters.classifications());
            }
            if (filters.minQualityScore() != null) {
                sql.append("  AND dx.quality_score >= ?\n");
                params.add(filters.minQualityScore());
            }
        }

        sql.append("ORDER BY de.embedding <=> ?::vector\nLIMIT ?");
        params.add(vectorLiteral);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), CONTEXT_MAPPER, params.toArray());
    }

    private static final RowMapper<DecisionExplanationContext> CONTEXT_MAPPER = (ResultSet rs, int rowNum) ->
            new DecisionExplanationContext(
                    rs.getLong("explanation_id"),
                    rs.getString("classification"),
                    rs.getString("outcome"),
                    rs.getString("decision_reason"),
                    rs.getString("decision_context_summary"),
                    rs.getString("policy_version"),
                    rs.getString("policy_family"),
                    rs.getDouble("quality_score"),
                    rs.getDouble("similarity_score")
            );

    @Override
    public boolean existsByExplanationIdAndModel(long explanationId, String embeddingModel) {
        String sql = "SELECT COUNT(*) FROM decision_embeddings WHERE decision_explanation_id = ? AND embedding_model = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, explanationId, embeddingModel);
        return count != null && count > 0;
    }

    @Override
    public int deleteByModelNot(String embeddingModel) {
        String sql = "DELETE FROM decision_embeddings WHERE embedding_model != ?";
        return jdbcTemplate.update(sql, embeddingModel);
    }

    @Override
    public int countByModel(String embeddingModel) {
        String sql = "SELECT COUNT(*) FROM decision_embeddings WHERE embedding_model = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, embeddingModel);
        return count != null ? count : 0;
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static float[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) {
            return new float[0];
        }
        String inner = vectorStr.substring(1, vectorStr.length() - 1);
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    private static final RowMapper<DecisionEmbedding> MAPPER = (ResultSet rs, int rowNum) -> {
        String vectorStr = rs.getString("embedding");
        float[] vector = parseVector(vectorStr);

        return new DecisionEmbedding(
                rs.getLong("id"),
                rs.getLong("decision_explanation_id"),
                vector,
                rs.getString("embedding_model"),
                rs.getTimestamp("created_at").toInstant()
        );
    };
}
