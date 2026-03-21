package com.triagemate.triage.control.rag;

import java.util.List;

public record RetrievalFilters(
        List<String> classifications,
        Double minQualityScore,
        String policyFamily,
        String minPolicyVersion
) {

    public static RetrievalFilters withDefaults(String policyFamily) {
        return new RetrievalFilters(List.of(), 0.5, policyFamily, null);
    }
}
