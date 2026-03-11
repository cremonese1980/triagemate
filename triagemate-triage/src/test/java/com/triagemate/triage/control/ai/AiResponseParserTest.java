package com.triagemate.triage.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseParserTest {

    private AiResponseParser parser;
    private final Set<String> allowed = Set.of("DEVICE_ERROR", "NETWORK_ISSUE", "NORMAL");

    @BeforeEach
    void setUp() {
        parser = new AiResponseParser(new ObjectMapper());
    }

    @Test
    void parsesValidJsonResponse() {
        String json = """
                {
                  "suggestedClassification": "DEVICE_ERROR",
                  "confidence": 0.92,
                  "reasoning": "Telemetry spike detected",
                  "recommendsOverride": true
                }
                """;
        AiClassificationResponse response = parser.parse(json, allowed);
        assertEquals("DEVICE_ERROR", response.suggestedClassification());
        assertEquals(0.92, response.confidence(), 0.001);
        assertEquals("Telemetry spike detected", response.reasoning());
        assertTrue(response.recommendsOverride());
    }

    @Test
    void parsesJsonFromMarkdownCodeBlock() {
        String markdown = """
                Here is the analysis:
                ```json
                {"suggestedClassification": "NORMAL", "confidence": 0.80, "reasoning": "Normal pattern", "recommendsOverride": false}
                ```
                """;
        AiClassificationResponse response = parser.parse(markdown, allowed);
        assertEquals("NORMAL", response.suggestedClassification());
    }

    @Test
    void ignoresExtraFields() {
        String json = """
                {
                  "suggestedClassification": "NORMAL",
                  "confidence": 0.75,
                  "reasoning": "ok",
                  "recommendsOverride": false,
                  "extraField": "should be ignored"
                }
                """;
        AiClassificationResponse response = parser.parse(json, allowed);
        assertEquals("NORMAL", response.suggestedClassification());
    }

    @Test
    void throwsOnEmptyResponse() {
        assertThrows(AiResponseParseException.class, () -> parser.parse("", allowed));
    }

    @Test
    void throwsOnNullResponse() {
        assertThrows(AiResponseParseException.class, () -> parser.parse(null, allowed));
    }

    @Test
    void throwsOnInvalidClassification() {
        String json = """
                {"suggestedClassification": "INVALID", "confidence": 0.9, "reasoning": "test", "recommendsOverride": false}
                """;
        assertThrows(AiResponseParseException.class, () -> parser.parse(json, allowed));
    }

    @Test
    void throwsOnConfidenceOutOfRange() {
        String json = """
                {"suggestedClassification": "NORMAL", "confidence": 1.5, "reasoning": "test", "recommendsOverride": false}
                """;
        assertThrows(AiResponseParseException.class, () -> parser.parse(json, allowed));
    }

    @Test
    void throwsOnMissingClassification() {
        String json = """
                {"confidence": 0.9, "reasoning": "test", "recommendsOverride": false}
                """;
        assertThrows(AiResponseParseException.class, () -> parser.parse(json, allowed));
    }

    @Test
    void acceptsAnyClassificationWhenAllowedSetEmpty() {
        String json = """
                {"suggestedClassification": "ANYTHING", "confidence": 0.9, "reasoning": "test", "recommendsOverride": false}
                """;
        AiClassificationResponse response = parser.parse(json, Set.of());
        assertEquals("ANYTHING", response.suggestedClassification());
    }
}
