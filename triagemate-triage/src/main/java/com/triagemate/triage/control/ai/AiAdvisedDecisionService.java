package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decorator around {@link DecisionService} that adds an AI advisory layer.
 * <p>
 * Flow: deterministic decision -> AI advisory -> validate -> audit -> assemble final result.
 * If AI fails for any reason, the deterministic result is returned unchanged.
 */
public class AiAdvisedDecisionService implements DecisionService {

    private static final Logger log = LoggerFactory.getLogger(AiAdvisedDecisionService.class);

    private final DecisionService delegate;
    private final AiDecisionAdvisor aiAdvisor;
    private final AiAdviceValidator adviceValidator;
    private final AiAuditService auditService;
    private final AiCostTracker costTracker;
    private final AiMetrics metrics;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Executor aiExecutor;
    private final AiAdvisoryProperties properties;

    public AiAdvisedDecisionService(
            DecisionService delegate,
            AiDecisionAdvisor aiAdvisor,
            AiAdviceValidator adviceValidator,
            AiAuditService auditService,
            AiCostTracker costTracker,
            AiMetrics metrics,
            CircuitBreaker circuitBreaker,
            Retry retry,
            Executor aiExecutor,
            AiAdvisoryProperties properties
    ) {
        this.delegate = delegate;
        this.aiAdvisor = aiAdvisor;
        this.adviceValidator = adviceValidator;
        this.auditService = auditService;
        this.costTracker = costTracker;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.aiExecutor = aiExecutor;
        this.properties = properties;
    }

    @Override
    public DecisionResult decide(DecisionContext<?> context) {
        // Step 1: Deterministic decision (existing pipeline)
        DecisionResult deterministicResult = delegate.decide(context);

        // Step 2: AI advisory (optional, bounded)
        AiDecisionAdvice advice = getAiAdvice(context, deterministicResult);

        // Step 3: Validate AI advice against policy
        ValidatedAdvice validated = adviceValidator.validate(deterministicResult, advice);

        // Step 4: Audit
        if (advice.isPresent()) {
            auditService.record(context, deterministicResult, advice, validated);
            metrics.recordCall(advice.provider(), "success", advice.latencyMs());
            if (advice.costUsd() > 0) {
                costTracker.recordCost(advice.costUsd());
                metrics.recordCost(advice.provider(), advice.costUsd());
            }
        }

        // Step 5: Record validation outcome metrics
        if (validated.isAccepted()) {
            metrics.recordAdviceAccepted();
        } else if (validated.status() == ValidatedAdvice.Status.REJECTED) {
            metrics.recordAdviceRejected();
        }

        // Step 6: Assemble final result
        return assembleResult(deterministicResult, validated);
    }

    private AiDecisionAdvice getAiAdvice(DecisionContext<?> context, DecisionResult deterministicResult) {
        CompletableFuture<AiDecisionAdvice> future = null;
        AtomicReference<Thread> aiThread = new AtomicReference<>();
        try {
            // Check budget before calling AI
            costTracker.checkBudget(properties.cost().maxPerDecisionUsd());

            long timeoutMs = properties.timeouts().advisory().toMillis();

            future = CompletableFuture.supplyAsync(
                    () -> {
                        aiThread.set(Thread.currentThread());
                        // Apply circuit breaker + retry around the actual AI call
                        return Retry.decorateSupplier(retry,
                                CircuitBreaker.decorateSupplier(circuitBreaker,
                                        () -> aiAdvisor.advise(context, deterministicResult))
                        ).get();
                    },
                    aiExecutor
            );

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (RejectedExecutionException e) {
            log.warn("AI executor queue full, falling back to deterministic decision");
            metrics.recordFallback("queue_full");
            auditService.recordError(context, "QUEUE_FULL", e.getMessage());
            return AiDecisionAdvice.NONE;

        } catch (CallNotPermittedException e) {
            log.warn("AI circuit breaker open, falling back to deterministic decision");
            metrics.recordFallback("circuit_breaker_open");
            auditService.recordError(context, "CIRCUIT_BREAKER_OPEN", e.getMessage());
            return AiDecisionAdvice.NONE;

        } catch (BudgetExceededException e) {
            log.warn("AI budget exceeded: {}", e.getMessage());
            metrics.recordFallback("budget_exceeded");
            auditService.recordError(context, "BUDGET_EXCEEDED", e.getMessage());
            return AiDecisionAdvice.NONE;

        } catch (Exception e) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            Thread t = aiThread.get();
            if (t != null) {
                t.interrupt();
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String errorType = cause instanceof TimeoutException ? "TIMEOUT" : "ERROR";
            log.warn("AI advisory failed ({}): {}", errorType, cause.getMessage());
            metrics.recordFallback(errorType.toLowerCase());
            metrics.recordCall(properties.provider(), "error", 0);
            auditService.recordError(context, errorType, cause.getMessage());
            return AiDecisionAdvice.NONE;
        }
    }

    private DecisionResult assembleResult(DecisionResult deterministicResult, ValidatedAdvice validated) {
        Map<String, Object> enrichedAttributes = new HashMap<>(deterministicResult.attributes());

        enrichedAttributes.put("aiAdvicePresent", validated.advice().isPresent());
        enrichedAttributes.put("aiAdviceAccepted", validated.isAccepted());
        enrichedAttributes.put("aiAdviceStatus", validated.status().name());

        if (validated.rejectionReason() != null) {
            enrichedAttributes.put("aiAdviceRejectedReason", validated.rejectionReason());
        }

        if (validated.advice().isPresent()) {
            enrichedAttributes.put("aiConfidence", validated.advice().confidence());
            enrichedAttributes.put("aiModelVersion", validated.advice().modelVersion());
            enrichedAttributes.put("aiPromptVersion", validated.advice().promptVersion());
        }

        // For now, AI accepted advice is only enrichment — not override.
        // Override behavior can be enabled in a future phase by using the AI's suggested classification.
        return DecisionResult.of(
                deterministicResult.outcome(),
                deterministicResult.reason(),
                enrichedAttributes,
                deterministicResult.reasonCode(),
                deterministicResult.humanReadableReason()
        );
    }
}
