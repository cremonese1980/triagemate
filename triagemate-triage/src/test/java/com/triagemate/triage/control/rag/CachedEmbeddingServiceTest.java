package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedEmbeddingServiceTest {

    private EmbeddingService delegate;
    private EmbeddingCacheRepository cacheRepository;
    private ContentHasher contentHasher;
    private CachedEmbeddingService service;

    @BeforeEach
    void setUp() {
        delegate = mock(EmbeddingService.class);
        cacheRepository = mock(EmbeddingCacheRepository.class);
        contentHasher = new ContentHasher();
        service = new CachedEmbeddingService(delegate, cacheRepository, contentHasher);

        when(delegate.getModelName()).thenReturn("nomic-embed-text");
    }

    @Test
    void cacheHitReturnsCachedVector() {
        String text = "classification: rule_a\nreason: some reason";
        String hash = contentHasher.hash(text);
        float[] cachedVector = {0.1f, 0.2f, 0.3f};

        EmbeddingCacheEntry entry = new EmbeddingCacheEntry(
                1L, hash, cachedVector, "nomic-embed-text", 3,
                Instant.now(), Instant.now(), 1
        );
        when(cacheRepository.findByContentHashAndModel(hash, "nomic-embed-text"))
                .thenReturn(Optional.of(entry));

        float[] result = service.generateEmbedding(text);

        assertArrayEquals(cachedVector, result);
        verify(cacheRepository).updateAccessMetadata(1L);
        verify(delegate, never()).generateEmbedding(any());
    }

    @Test
    void cacheMissCallsDelegateAndSaves() {
        String text = "classification: rule_b\nreason: another reason";
        String hash = contentHasher.hash(text);
        float[] generatedVector = {0.4f, 0.5f, 0.6f};

        when(cacheRepository.findByContentHashAndModel(hash, "nomic-embed-text"))
                .thenReturn(Optional.empty());
        when(delegate.generateEmbedding(text)).thenReturn(generatedVector);

        float[] result = service.generateEmbedding(text);

        assertArrayEquals(generatedVector, result);
        verify(delegate).generateEmbedding(text);
        verify(cacheRepository).save(any(EmbeddingCacheEntry.class));
    }

    @Test
    void nullTextReturnsEmptyWithoutCacheLookup() {
        float[] result = service.generateEmbedding(null);

        assertEquals(0, result.length);
        verifyNoInteractions(cacheRepository);
        verify(delegate, never()).generateEmbedding(any());
    }

    @Test
    void blankTextReturnsEmptyWithoutCacheLookup() {
        float[] result = service.generateEmbedding("   ");

        assertEquals(0, result.length);
        verifyNoInteractions(cacheRepository);
        verify(delegate, never()).generateEmbedding(any());
    }

    @Test
    void cacheSaveFailureDoesNotPreventReturn() {
        String text = "some text";
        String hash = contentHasher.hash(text);
        float[] generatedVector = {0.7f, 0.8f};

        when(cacheRepository.findByContentHashAndModel(hash, "nomic-embed-text"))
                .thenReturn(Optional.empty());
        when(delegate.generateEmbedding(text)).thenReturn(generatedVector);
        doThrow(new RuntimeException("DB failure")).when(cacheRepository).save(any());

        float[] result = service.generateEmbedding(text);

        assertArrayEquals(generatedVector, result);
    }

    @Test
    void delegatesGetModelName() {
        assertEquals("nomic-embed-text", service.getModelName());
    }
}
