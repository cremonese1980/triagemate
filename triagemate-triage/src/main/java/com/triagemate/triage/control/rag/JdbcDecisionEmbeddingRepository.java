package com.triagemate.triage.control.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
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
