package com.triagemate.triage.control.rag;

import java.util.Optional;

public interface EmbeddingCacheRepository {

    long save(EmbeddingCacheEntry entry);

    Optional<EmbeddingCacheEntry> findByContentHashAndModel(String contentHash, String embeddingModel);

    void updateAccessMetadata(long id);
}
