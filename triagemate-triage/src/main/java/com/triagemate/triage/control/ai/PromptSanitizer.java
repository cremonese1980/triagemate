package com.triagemate.triage.control.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptSanitizer {

    private static final int MAX_INPUT_LENGTH = 2000;

    public String sanitize(String input) {
        if (input == null) return "";
        String cleaned = input
                // Prompt injection patterns
                .replaceAll("(?i)(ignore|disregard|forget).{0,20}(previous|above|instructions)", "[FILTERED]")
                .replaceAll("(?i)(system:|assistant:|user:)", "[FILTERED]")
                // Basic PII patterns
                .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]")
                .replaceAll("\\b\\d{3}-\\d{2}-\\d{4}\\b", "[SSN]")
                .replaceAll("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b", "[CARD]");
        return cleaned.substring(0, Math.min(cleaned.length(), MAX_INPUT_LENGTH));
    }
}
