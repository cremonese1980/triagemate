package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagPropertiesTest {

    @Test
    void defaultValues() {
        RagProperties props = new RagProperties(false, null, 0, 0, 0.0, null);

        assertFalse(props.enabled());
        assertEquals("nomic-embed-text", props.embeddingModel());
        assertEquals(768, props.embeddingDimension());
        assertEquals(500, props.maxContextSummaryLength());
        assertEquals(0.5, props.defaultQualityScore());
        assertNotNull(props.retrieval());
        assertEquals(3, props.retrieval().topK());
        assertEquals(0.5, props.retrieval().minQualityScore());
        assertNull(props.retrieval().minPolicyVersion());
    }

    @Test
    void customValues() {
        RagProperties.Retrieval retrieval = new RagProperties.Retrieval(5, 0.7, "2.0.0");
        RagProperties props = new RagProperties(true, "text-embedding-3-small", 1536, 800, 0.7, retrieval);

        assertTrue(props.enabled());
        assertEquals("text-embedding-3-small", props.embeddingModel());
        assertEquals(1536, props.embeddingDimension());
        assertEquals(800, props.maxContextSummaryLength());
        assertEquals(0.7, props.defaultQualityScore());
        assertEquals(5, props.retrieval().topK());
        assertEquals(0.7, props.retrieval().minQualityScore());
        assertEquals("2.0.0", props.retrieval().minPolicyVersion());
    }

    @Test
    void retrievalDefaults() {
        RagProperties.Retrieval retrieval = new RagProperties.Retrieval(0, 0.0, null);

        assertEquals(3, retrieval.topK());
        assertEquals(0.5, retrieval.minQualityScore());
        assertNull(retrieval.minPolicyVersion());
    }
}
