package com.triagemate.triage.control.policy;

import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.control.decision.DecisionContext;

import java.util.Locale;
import java.util.Map;

public class BasicTriagePolicy implements Policy {

    @Override
    public PolicyResult evaluate(DecisionContext<?> context) {

        InputReceivedV1 payload = ((InputReceivedV1) context.payload());

        String text = safeLower(payload.text());
        String subject = safeLower(payload.subject());
        String channel = safeLower(payload.channel());

        // ---- RULE 1: hard reject (error/failure)
        if (containsAny(text, "error", "fail", "broken", "not working")) {
            return PolicyResult.deny("RULE_ERROR_KEYWORDS", Map.of("rule", "basic policy"));
        }

        // ---- RULE 2: urgent email → reject (simulate escalation)
        if ("email".equals(channel) && containsAny(subject, "urgent", "asap", "immediate")) {
            return PolicyResult.deny("RULE_URGENT_EMAIL", Map.of("rule", "basic policy"));
        }

        // ---- RULE 3: weak signal → accept low priority
        if (containsAny(text, "maybe", "not sure", "first day")) {
            return PolicyResult.allow("RULE_WEAK_SIGNAL");
        }

        // ---- DEFAULT
        return PolicyResult.allow("RULE_DEFAULT_ACCEPT");
    }

    private String safeLower(Object o) {
        return o == null ? "" : o.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}