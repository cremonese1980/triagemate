package com.triagemate.triage.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReplayResult(
        UUID originalDecisionId,
        String originalOutcome,
        String originalPolicyVersion,
        Map<String, Object> originalAttributes,
        String newOutcome,
        String newPolicyVersion,
        Map<String, Object> newAttributes,
        boolean driftDetected,
        List<String> attributeDifferences
) {
    private static final String DECISION_ID_KEY = "decisionId";

    public ReplayResult {
        originalAttributes = originalAttributes == null ? Map.of() : Map.copyOf(originalAttributes);
        newAttributes = newAttributes == null ? Map.of() : Map.copyOf(newAttributes);
        attributeDifferences = attributeDifferences == null ? List.of() : List.copyOf(attributeDifferences);
    }

    public static ReplayResult compare(
            UUID originalDecisionId,
            String originalOutcome, String originalPolicyVersion, Map<String, Object> originalAttributes,
            String newOutcome, String newPolicyVersion, Map<String, Object> newAttributes
    ) {
        Map<String, Object> safeOriginal = originalAttributes == null ? Map.of() : originalAttributes;
        Map<String, Object> safeNew = newAttributes == null ? Map.of() : newAttributes;
        List<String> differences = computeDifferences(safeOriginal, safeNew);
        boolean driftDetected = !Objects.equals(originalOutcome, newOutcome)
                || !Objects.equals(originalPolicyVersion, newPolicyVersion)
                || !differences.isEmpty();

        return new ReplayResult(
                originalDecisionId,
                originalOutcome, originalPolicyVersion, originalAttributes,
                newOutcome, newPolicyVersion, newAttributes,
                driftDetected, differences
        );
    }

    private static List<String> computeDifferences(
            Map<String, Object> original, Map<String, Object> current
    ) {
        List<String> diffs = new ArrayList<>();

        for (Map.Entry<String, Object> entry : original.entrySet()) {
            String key = entry.getKey();
            if (DECISION_ID_KEY.equals(key)) {
                continue;
            }
            if (!current.containsKey(key)) {
                diffs.add("removed: " + key);
            } else if (!Objects.equals(entry.getValue(), current.get(key))) {
                diffs.add("changed: " + key + " [" + entry.getValue() + " -> " + current.get(key) + "]");
            }
        }
        for (String key : current.keySet()) {
            if (DECISION_ID_KEY.equals(key)) {
                continue;
            }
            if (!original.containsKey(key)) {
                diffs.add("added: " + key);
            }
        }

        return diffs;
    }
}
