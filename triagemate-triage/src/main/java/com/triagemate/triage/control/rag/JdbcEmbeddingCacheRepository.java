package com.triagemate.triage.control.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcEmbeddingCacheRepository implements EmbeddingCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEmbeddingCacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long save(EmbeddingCacheEntry entry) {
        String sql = """
                INSERT INTO embedding_cache
                    (content_hash, embedding_vector, embedding_model, dimension,
                     created_at, last_accessed_at, access_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        Long id = jdbcTemplate.execute((Connection connection) -> {
            Array pgArray = connection.createArrayOf("float8", toDoubleArray(entry.embeddingVector()));
            return jdbcTemplate.queryForObject(sql, Long.class,
                    entry.contentHash(),
                    pgArray,
                    entry.embeddingModel(),
                    entry.dimension(),
                    Timestamp.from(entry.createdAt()),
                    Timestamp.from(entry.lastAccessedAt()),
                    entry.accessCount()
            );
        });

        return id != null ? id : -1;
    }

    @Override
    public Optional<EmbeddingCacheEntry> findByContentHashAndModel(String contentHash, String embeddingModel) {
        String sql = "SELECT * FROM embedding_cache WHERE content_hash = ? AND embedding_model = ?";
        List<EmbeddingCacheEntry> results = jdbcTemplate.query(sql, MAPPER, contentHash, embeddingModel);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void updateAccessMetadata(long id) {
        String sql = "UPDATE embedding_cache SET last_accessed_at = NOW(), access_count = access_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static Double[] toDoubleArray(float[] floats) {
        Double[] doubles = new Double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = (double) floats[i];
        }
        return doubles;
    }

    private static float[] toFloatArray(Double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = doubles[i].floatValue();
        }
        return floats;
    }

    private static final RowMapper<EmbeddingCacheEntry> MAPPER = (ResultSet rs, int rowNum) -> {
        Array sqlArray = rs.getArray("embedding_vector");
        Double[] doubles = (Double[]) sqlArray.getArray();
        float[] vector = toFloatArray(doubles);

        return new EmbeddingCacheEntry(
                rs.getLong("id"),
                rs.getString("content_hash"),
                vector,
                rs.getString("embedding_model"),
                rs.getInt("dimension"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_accessed_at").toInstant(),
                rs.getInt("access_count")
        );
    };
}
