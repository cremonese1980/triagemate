package com.triagemate.triage.control.rag;

import java.util.List;
import java.util.Optional;

public interface DecisionExplanationRepository {

    long save(DecisionExplanation explanation);

    boolean existsByContentHash(String contentHash);

    Optional<DecisionExplanation> findById(long id);

    List<DecisionExplanation> findByClassification(String classification);

    List<DecisionExplanation> findAllNonArchived();

    List<DecisionExplanation> findNonArchivedBatch(long afterId, int batchSize);

    int countNonArchived();
}
