package com.triagemate.triage.control.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiClassificationResponse parse(String rawResponse, Set<String> allowedClassifications) {
        try {
            String json = extractJson(rawResponse);
            AiClassificationResponse response = objectMapper.readValue(json, AiClassificationResponse.class);
            validate(response, allowedClassifications);
            return response;
        } catch (AiResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new AiResponseParseException("Invalid AI response: " + e.getMessage(), e);
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiResponseParseException("Empty AI response", null);
        }
        String trimmed = raw.strip();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            throw new AiResponseParseException("No JSON object found in AI response", null);
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;

        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        throw new AiResponseParseException("Unterminated JSON object in AI response", null);
    }

    private void validate(AiClassificationResponse response, Set<String> allowedClassifications) {
        if (response.suggestedClassification() == null || response.suggestedClassification().isBlank()) {
            throw new AiResponseParseException("Missing suggestedClassification", null);
        }
        if (!allowedClassifications.isEmpty()
                && !allowedClassifications.contains(response.suggestedClassification())) {
            throw new AiResponseParseException(
                    "Invalid classification: " + response.suggestedClassification(), null);
        }
        if (response.confidence() < 0.0 || response.confidence() > 1.0) {
            throw new AiResponseParseException(
                    "Confidence out of range: " + response.confidence(), null);
        }
    }
}
