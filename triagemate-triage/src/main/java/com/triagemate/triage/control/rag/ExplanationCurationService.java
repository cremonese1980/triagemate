package com.triagemate.triage.control.rag;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.policy.PolicyFamilyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExplanationCurationService {

    private static final Logger log = LoggerFactory.getLogger(ExplanationCurationService.class);
    private static final Set<DecisionOutcome> CURATABLE_OUTCOMES = Set.of(
            DecisionOutcome.ACCEPT, DecisionOutcome.REJECT
    );
    private static final Set<String> GENERIC_REASONS = Set.of(
            "", "unknown", "default", "none", "n/a"
    );
    private static final int MAX_CONTEXT_SUMMARY_LENGTH = 500;

    private final DecisionExplanationRepository repository;
    private final PolicyFamilyProvider policyFamilyProvider;
    private final ContentHasher contentHasher;
    private final double defaultQualityScore;

    public ExplanationCurationService(
            DecisionExplanationRepository repository,
            PolicyFamilyProvider policyFamilyProvider,
            ContentHasher contentHasher,
            double defaultQualityScore
    ) {
        this.repository = repository;
        this.policyFamilyProvider = policyFamilyProvider;
        this.contentHasher = contentHasher;
        this.defaultQualityScore = defaultQualityScore;
    }

    public void curateFromDecision(DecisionResult result, DecisionContext<?> context) {
        if (!isCuratable(result)) {
            log.debug("Decision not curatable: outcome={}, reason={}",
                    result.outcome(), result.reason());
            return;
        }

        String classification = resolveClassification(result);
        String reason = result.humanReadableReason() != null
                ? result.humanReadableReason()
                : result.reason();

        if (!hasSubstantiveReason(reason)) {
            log.debug("Decision reason not substantive, skipping curation");
            return;
        }

        String contentHash = computeContentHash(classification, reason);

        if (repository.existsByContentHash(contentHash)) {
            log.debug("Duplicate explanation detected, skipping curation contentHash={}", contentHash);
            return;
        }

        String contextSummary = buildContextSummary(context);
        String decisionId = resolveDecisionId(result);

        DecisionExplanation explanation = DecisionExplanation.create(
                decisionId,
                result.policyVersion(),
                policyFamilyProvider.currentFamily(),
                classification,
                result.outcome().name(),
                reason,
                contextSummary,
                contentHash,
                defaultQualityScore
        );

        long id = repository.save(explanation);
        log.info("Decision explanation curated id={} decisionId={} classification={}",
                id, decisionId, classification);
    }

    boolean isCuratable(DecisionResult result) {
        return result != null
                && result.outcome() != null
                && CURATABLE_OUTCOMES.contains(result.outcome());
    }

    boolean hasSubstantiveReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return !GENERIC_REASONS.contains(normalized);
    }

    String computeContentHash(String classification, String reason) {
        String input = (classification != null ? classification : "") + "|" + (reason != null ? reason : "");
        return contentHasher.hash(input);
    }

    String buildContextSummary(DecisionContext<?> context) {
        if (context == null || context.payload() == null) {
            return null;
        }
        String summary = context.payload().toString();
        if (summary.length() > MAX_CONTEXT_SUMMARY_LENGTH) {
            return summary.substring(0, MAX_CONTEXT_SUMMARY_LENGTH);
        }
        return summary;
    }

    String resolveClassification(DecisionResult result) {
        Map<String, Object> attrs = result.attributes();

        // If AI override was applied, use the AI suggested classification
        if (Boolean.TRUE.equals(attrs.get("aiOverrideApplied"))) {
            Object aiClassification = attrs.get("aiSuggestedClassification");
            if (aiClassification instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // Otherwise use the reasonCode from deterministic policy
        if (result.reasonCode() != null) {
            return result.reasonCode().name();
        }

        return result.reason();
    }

    private String resolveDecisionId(DecisionResult result) {
        Object raw = result.attributes().get("decisionId");
        if (raw instanceof String s && !s.isBlank()) {
            return s;
        }
        return "unknown";
    }
}
