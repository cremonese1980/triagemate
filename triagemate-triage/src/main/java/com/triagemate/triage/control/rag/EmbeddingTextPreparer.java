package com.triagemate.triage.control.rag;

import java.util.Locale;

public class EmbeddingTextPreparer {

    private static final int MAX_CONTEXT_SUMMARY_LENGTH = 500;

    public String prepare(String decisionReason, String classification, String contextSummary) {
        StringBuilder sb = new StringBuilder();

        if (classification != null && !classification.isBlank()) {
            sb.append("classification: ").append(normalize(classification)).append("\n");
        }

        if (decisionReason != null && !decisionReason.isBlank()) {
            sb.append("reason: ").append(normalize(decisionReason)).append("\n");
        }

        if (contextSummary != null && !contextSummary.isBlank()) {
            String truncated = truncate(contextSummary, MAX_CONTEXT_SUMMARY_LENGTH);
            sb.append("context: ").append(normalize(truncated));
        }

        return sb.toString().trim();
    }

    String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
