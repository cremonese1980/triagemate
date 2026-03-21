package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.rag.DecisionExplanationContext;

import java.util.List;

public class HistoricalContextFormatter {

    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final String HEADER = "\nSIMILAR HISTORICAL DECISIONS:";
    private static final String FOOTER = "\nConsider these historical patterns in your analysis.";

    public String format(List<DecisionExplanationContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        int budget = MAX_CONTEXT_CHARS - HEADER.length() - FOOTER.length();

        for (int i = 0; i < contexts.size(); i++) {
            DecisionExplanationContext ctx = contexts.get(i);
            String entry = formatEntry(i + 1, ctx);

            if (sb.length() - HEADER.length() - 1 + entry.length() > budget) {
                break;
            }
            sb.append(entry);
        }

        if (sb.length() <= HEADER.length() + 1) {
            return "";
        }

        sb.append(FOOTER);
        return sb.toString();
    }

    private String formatEntry(int index, DecisionExplanationContext ctx) {
        String reason = ctx.decisionReason() != null ? ctx.decisionReason() : "";
        if (reason.length() > 200) {
            reason = reason.substring(0, 200) + "...";
        }

        return String.format("%d. [Classification: %s] Reasoning: %s\n   Outcome: %s\n\n",
                index,
                ctx.classification() != null ? ctx.classification() : "UNKNOWN",
                reason,
                ctx.outcome() != null ? ctx.outcome() : "UNKNOWN"
        );
    }
}
