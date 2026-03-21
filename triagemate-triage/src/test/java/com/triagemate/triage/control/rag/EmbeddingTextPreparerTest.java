package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingTextPreparerTest {

    private EmbeddingTextPreparer preparer;

    @BeforeEach
    void setUp() {
        preparer = new EmbeddingTextPreparer();
    }

    @Test
    void preparesStandardText() {
        String result = preparer.prepare("Error keywords detected", "RULE_ERROR", "Device telemetry spike");

        assertTrue(result.contains("classification: rule_error"));
        assertTrue(result.contains("reason: error keywords detected"));
        assertTrue(result.contains("context: device telemetry spike"));
    }

    @Test
    void normalizesWhitespace() {
        String result = preparer.prepare("multiple   spaces\n\nnewlines", "RULE_A", null);

        assertTrue(result.contains("reason: multiple spaces newlines"));
    }

    @Test
    void truncatesLongContextSummary() {
        String longContext = "x".repeat(600);
        String result = preparer.prepare("reason", "CLASS", longContext);

        String contextLine = result.lines()
                .filter(l -> l.startsWith("context: "))
                .findFirst()
                .orElse("");

        // "context: " prefix is 9 chars, plus max 500 chars of content
        assertTrue(contextLine.length() <= 509);
    }

    @Test
    void handlesNullClassification() {
        String result = preparer.prepare("some reason", null, "context");

        assertFalse(result.contains("classification:"));
        assertTrue(result.contains("reason: some reason"));
    }

    @Test
    void handlesNullContextSummary() {
        String result = preparer.prepare("reason text", "CLASS", null);

        assertFalse(result.contains("context:"));
        assertTrue(result.contains("classification: class"));
        assertTrue(result.contains("reason: reason text"));
    }

    @Test
    void handlesEmptyReason() {
        String result = preparer.prepare("", "CLASS", "context");

        assertFalse(result.contains("reason:"));
        assertTrue(result.contains("classification: class"));
    }

    @Test
    void handlesAllNulls() {
        String result = preparer.prepare(null, null, null);
        assertEquals("", result);
    }
}
