package com.triagemate.triage.control.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateServiceTest {

    private final PromptTemplateService service = new PromptTemplateService();

    @Test
    void rendersVariables() {
        String result = service.render(Map.of(
                "classification", "ACCEPT",
                "outcome", "ACCEPT",
                "reason", "all policies passed",
                "eventType", "device.telemetry",
                "payloadSummary", "temperature=42",
                "allowedClassifications", "DEVICE_ERROR, NORMAL"
        ));

        assertTrue(result.contains("Classification: ACCEPT"));
        assertTrue(result.contains("Outcome: ACCEPT"));
        assertTrue(result.contains("Reason: all policies passed"));
        assertTrue(result.contains("Event Type: device.telemetry"));
        assertTrue(result.contains("Payload Summary: temperature=42"));
        assertTrue(result.contains("DEVICE_ERROR, NORMAL"));
    }

    @Test
    void promptVersionIsSet() {
        assertEquals("1.0.0", service.getPromptVersion());
    }

    @Test
    void promptHashIsConsistent() {
        String hash1 = service.getPromptHash();
        String hash2 = new PromptTemplateService().getPromptHash();
        assertEquals(hash1, hash2);
        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 hex
    }
}
