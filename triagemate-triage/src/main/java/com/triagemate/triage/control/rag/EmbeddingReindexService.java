package com.triagemate.triage.control.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EmbeddingReindexService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingReindexService.class);

    private final EmbeddingService embeddingService;
    private final EmbeddingTextPreparer textPreparer;
    private final DecisionExplanationRepository explanationRepository;
    private final DecisionEmbeddingRepository embeddingRepository;

    public EmbeddingReindexService(
            EmbeddingService embeddingService,
            EmbeddingTextPreparer textPreparer,
            DecisionExplanationRepository explanationRepository,
            DecisionEmbeddingRepository embeddingRepository
    ) {
        this.embeddingService = embeddingService;
        this.textPreparer = textPreparer;
        this.explanationRepository = explanationRepository;
        this.embeddingRepository = embeddingRepository;
    }

    public ReindexResult reindex() {
        String currentModel = embeddingService.getModelName();
        List<DecisionExplanation> explanations = explanationRepository.findAllNonArchived();

        log.info("Starting embedding reindex for model={}, explanations={}", currentModel, explanations.size());

        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (DecisionExplanation exp : explanations) {
            try {
                if (embeddingRepository.existsByExplanationIdAndModel(exp.id(), currentModel)) {
                    skipped++;
                    continue;
                }

                String text = textPreparer.prepare(
                        exp.decisionReason(), exp.classification(), exp.decisionContextSummary());
                float[] embedding = embeddingService.generateEmbedding(text);

                if (embedding.length == 0) {
                    log.warn("Empty embedding for explanation id={}, skipping", exp.id());
                    failed++;
                    continue;
                }

                embeddingRepository.save(DecisionEmbedding.create(exp.id(), embedding, currentModel));
                created++;
            } catch (Exception e) {
                log.warn("Failed to reindex explanation id={}", exp.id(), e);
                failed++;
            }
        }

        log.info("Embedding reindex complete model={} created={} skipped={} failed={}",
                currentModel, created, skipped, failed);

        return new ReindexResult(currentModel, created, skipped, failed);
    }

    public int purgeOldModelEmbeddings(String currentModel) {
        int deleted = embeddingRepository.deleteByModelNot(currentModel);
        log.info("Purged {} old model embeddings, retained model={}", deleted, currentModel);
        return deleted;
    }

    public boolean isReindexNeeded() {
        String currentModel = embeddingService.getModelName();
        int currentCount = embeddingRepository.countByModel(currentModel);
        int explanationCount = explanationRepository.countNonArchived();
        return currentCount < explanationCount && explanationCount > 0;
    }
}
