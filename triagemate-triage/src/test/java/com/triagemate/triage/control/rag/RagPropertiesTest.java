package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagPropertiesTest {

    @Test
    void defaultValues() {
        RagProperties props = new RagProperties(false, null, 0, 0, 0.0);

        assertFalse(props.enabled());
        assertEquals("nomic-embed-text", props.embeddingModel());
        assertEquals(768, props.embeddingDimension());
        assertEquals(500, props.maxContextSummaryLength());
        assertEquals(0.5, props.defaultQualityScore());
    }

    @Test
    void customValues() {
        RagProperties props = new RagProperties(true, "text-embedding-3-small", 1536, 800, 0.7);

        assertTrue(props.enabled());
        assertEquals("text-embedding-3-small", props.embeddingModel());
        assertEquals(1536, props.embeddingDimension());
        assertEquals(800, props.maxContextSummaryLength());
        assertEquals(0.7, props.defaultQualityScore());
    }
}
