package com.triagemate.triage.control.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    void sameInputProducesSameHash() {
        String hash1 = hasher.hash("test input");
        String hash2 = hasher.hash("test input");
        assertEquals(hash1, hash2);
    }

    @Test
    void differentInputProducesDifferentHash() {
        String hash1 = hasher.hash("input A");
        String hash2 = hasher.hash("input B");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashIs64CharHex() {
        String hash = hasher.hash("some content");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void nullInputProducesHash() {
        String hash = hasher.hash(null);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void emptyInputProducesHash() {
        String hash = hasher.hash("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void nullAndEmptyProduceSameHash() {
        assertEquals(hasher.hash(null), hasher.hash(""));
    }
}
