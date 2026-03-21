package com.triagemate.triage.control.rag;

import com.triagemate.testsupport.JdbcIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(JdbcEmbeddingCacheRepository.class)
class EmbeddingCacheRepositoryIT extends JdbcIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcEmbeddingCacheRepository repository;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM embedding_cache");
    }

    @Test
    void savesAndFindsByContentHashAndModel() {
        EmbeddingCacheEntry entry = EmbeddingCacheEntry.create(
                "hash-001", new float[]{0.1f, 0.2f, 0.3f}, "nomic-embed-text", 3
        );

        long id = repository.save(entry);
        assertThat(id).isPositive();

        Optional<EmbeddingCacheEntry> found = repository.findByContentHashAndModel(
                "hash-001", "nomic-embed-text"
        );
        assertThat(found).isPresent();
        assertThat(found.get().contentHash()).isEqualTo("hash-001");
        assertThat(found.get().embeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(found.get().dimension()).isEqualTo(3);
        assertThat(found.get().accessCount()).isEqualTo(1);
        assertThat(found.get().embeddingVector()).hasSize(3);
    }

    @Test
    void findByContentHashAndModelReturnsEmptyWhenNotFound() {
        Optional<EmbeddingCacheEntry> found = repository.findByContentHashAndModel(
                "nonexistent-hash", "nomic-embed-text"
        );
        assertThat(found).isEmpty();
    }

    @Test
    void findByContentHashAndModelDistinguishesModels() {
        repository.save(EmbeddingCacheEntry.create(
                "hash-shared", new float[]{0.1f}, "model-a", 1
        ));
        repository.save(EmbeddingCacheEntry.create(
                "hash-shared", new float[]{0.9f}, "model-b", 1
        ));

        Optional<EmbeddingCacheEntry> foundA = repository.findByContentHashAndModel("hash-shared", "model-a");
        Optional<EmbeddingCacheEntry> foundB = repository.findByContentHashAndModel("hash-shared", "model-b");

        assertThat(foundA).isPresent();
        assertThat(foundB).isPresent();
        assertThat(foundA.get().embeddingVector()[0]).isEqualTo(0.1f);
        assertThat(foundB.get().embeddingVector()[0]).isEqualTo(0.9f);
    }

    @Test
    void updateAccessMetadataIncrementsCountAndUpdatesTimestamp() {
        long id = repository.save(EmbeddingCacheEntry.create(
                "hash-access", new float[]{0.5f}, "nomic-embed-text", 1
        ));

        repository.updateAccessMetadata(id);
        repository.updateAccessMetadata(id);

        Optional<EmbeddingCacheEntry> found = repository.findByContentHashAndModel(
                "hash-access", "nomic-embed-text"
        );
        assertThat(found).isPresent();
        assertThat(found.get().accessCount()).isEqualTo(3);
    }

    @Test
    void uniqueConstraintPreventsHashModelDuplicates() {
        repository.save(EmbeddingCacheEntry.create(
                "hash-dup", new float[]{0.1f}, "nomic-embed-text", 1
        ));

        assertThatThrownBy(() -> repository.save(EmbeddingCacheEntry.create(
                "hash-dup", new float[]{0.2f}, "nomic-embed-text", 1
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesVectorPrecision() {
        float[] vector = {0.123456789f, -0.987654321f, 0.0f, 1.0f, -1.0f};
        repository.save(EmbeddingCacheEntry.create(
                "hash-precision", vector, "nomic-embed-text", 5
        ));

        Optional<EmbeddingCacheEntry> found = repository.findByContentHashAndModel(
                "hash-precision", "nomic-embed-text"
        );
        assertThat(found).isPresent();
        float[] retrieved = found.get().embeddingVector();
        assertThat(retrieved).hasSize(5);
        for (int i = 0; i < vector.length; i++) {
            assertThat(retrieved[i]).isEqualTo(vector[i], org.assertj.core.data.Offset.offset(1e-6f));
        }
    }
}
