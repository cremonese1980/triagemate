package com.triagemate.triage.control.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateServiceTest {

    private final PromptTemplateService service = new PromptTemplateService();

    @Test
    void rendersVariables() {
        var variables = new java.util.HashMap<String, String>();
        variables.put("classification", "ACCEPT");
        variables.put("outcome", "ACCEPT");
        variables.put("reason", "all policies passed");
        variables.put("eventType", "device.telemetry");
        variables.put("payloadSummary", "temperature=42");
        variables.put("classificationRule", "one of: DEVICE_ERROR, NORMAL");
        variables.put("historicalContext", "");

        String result = service.render(variables);

        assertTrue(result.contains("Classification: ACCEPT"));
        assertTrue(result.contains("Outcome: ACCEPT"));
        assertTrue(result.contains("Reason: all policies passed"));
        assertTrue(result.contains("Event Type: device.telemetry"));
        assertTrue(result.contains("Payload Summary: temperature=42"));
        assertTrue(result.contains("one of: DEVICE_ERROR, NORMAL"));
    }

    @Test
    void rendersHistoricalContext() {
        var variables = new java.util.HashMap<String, String>();
        variables.put("classification", "ACCEPT");
        variables.put("outcome", "ACCEPT");
        variables.put("reason", "test");
        variables.put("eventType", "test");
        variables.put("payloadSummary", "test");
        variables.put("classificationRule", "free-form");
        variables.put("historicalContext", "\nSIMILAR HISTORICAL DECISIONS:\n1. [Classification: DEVICE_ERROR] Reasoning: Spike\n   Outcome: ACCEPT\n");

        String result = service.render(variables);

        assertTrue(result.contains("SIMILAR HISTORICAL DECISIONS:"));
        assertTrue(result.contains("[Classification: DEVICE_ERROR]"));
    }

    @Test
    void promptVersionIsSet() {
        assertEquals("1.1.0", service.getPromptVersion());
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
