package com.triagemate.triage.control.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CachedEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CachedEmbeddingService.class);

    private final EmbeddingService delegate;
    private final EmbeddingCacheRepository cacheRepository;
    private final ContentHasher contentHasher;

    public CachedEmbeddingService(
            EmbeddingService delegate,
            EmbeddingCacheRepository cacheRepository,
            ContentHasher contentHasher
    ) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.contentHasher = contentHasher;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        String hash = contentHasher.hash(text);
        String model = delegate.getModelName();

        Optional<EmbeddingCacheEntry> cached = cacheRepository.findByContentHashAndModel(hash, model);
        if (cached.isPresent()) {
            cacheRepository.updateAccessMetadata(cached.get().id());
            log.debug("Embedding cache hit contentHash={} model={}", hash, model);
            return cached.get().embeddingVector();
        }

        float[] embedding = delegate.generateEmbedding(text);

        try {
            cacheRepository.save(EmbeddingCacheEntry.create(hash, embedding, model, embedding.length));
            log.debug("Embedding cache miss, generated and cached contentHash={} model={}", hash, model);
        } catch (Exception e) {
            log.warn("Failed to cache embedding contentHash={} model={}", hash, model, e);
        }

        return embedding;
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }
}
