package com.triagemate.triage.control.rag;

import java.util.List;

public interface DecisionMemoryService {

    List<DecisionExplanationContext> findSimilarDecisions(
            String query, int topK, RetrievalFilters filters);
}
