package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive resilience tests for Phase 12A.3 — Deterministic Fallback & Error Handling.
 *
 * Covers verification scenarios V2–V5:
 * - Circuit breaker state transitions (V4)
 * - Retry behavior with transient/permanent exception differentiation
 * - HTTP error classification
 * - Executor queue rejection
 * - Fallback metrics tracking
 */
class AiFallbackAndResilienceTest {

    private SimpleMeterRegistry meterRegistry;
    private AiAdvisoryProperties properties;
    private AiAdvisedDecisionServiceTest.StubDecisionService stubDelegate;
    private TestAiAuditService stubAudit;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new AiAdvisoryProperties(
                true, "test", null, null,
                Set.of("DEVICE_ERROR", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        stubDelegate = new AiAdvisedDecisionServiceTest.StubDecisionService();
        stubAudit = new TestAiAuditService();
    }

    // ──────────────────────────────────────────────────────────────
    // Group A: Circuit Breaker State Transitions (V4)
    // ──────────────────────────────────────────────────────────────

    @Nested
    class CircuitBreakerTests {

        @Test
        void circuitBreaker_opensAfterFailures_thenFastFails() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .recordExceptions(TransientAiException.class)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("cb-test");

            // Retry with 1 attempt (no actual retry) to isolate circuit breaker behavior
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("no-retry");

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new TransientAiException("server error"));

            AiAdvisedDecisionService service = buildService(advisor, cb, noRetry);

            // Drive 4 failures -> circuit breaker should open (100% failure rate > 50% threshold)
            for (int i = 0; i < 4; i++) {
                DecisionResult result = service.decide(createContext());
                assertEquals(DecisionOutcome.ACCEPT, result.outcome());
                assertEquals(false, result.attributes().get("aiAdvicePresent"));
            }

            assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                    "Circuit breaker should be OPEN after 4 consecutive failures");

            // Next call should fast-fail (CallNotPermittedException)
            DecisionResult fastFailResult = service.decide(createContext());
            assertEquals(DecisionOutcome.ACCEPT, fastFailResult.outcome());
            assertEquals(false, fastFailResult.attributes().get("aiAdvicePresent"));

            // Verify circuit_breaker_open metric
            double cbOpenCount = countMetric("triagemate.ai.fallback.total", "reason", "circuit_breaker_open");
            assertTrue(cbOpenCount >= 1, "Expected circuit_breaker_open fallback metric >= 1, got " + cbOpenCount);
        }

        @Test
        void circuitBreaker_recoversAfterHalfOpen() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofMillis(100))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .recordExceptions(TransientAiException.class)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("cb-recovery");

            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("no-retry-recovery");

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new TransientAiException("server error"));

            AiAdvisedDecisionService service = buildService(advisor, cb, noRetry);

            // Drive circuit breaker to OPEN
            for (int i = 0; i < 4; i++) {
                service.decide(createContext());
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            // Transition to HALF_OPEN manually
            cb.transitionToHalfOpenState();
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

            // Now make advisor return success
            advisor.setThrowException(null);
            advisor.setAdvice(validAdvice());

            DecisionResult result = service.decide(createContext());
            assertEquals(true, result.attributes().get("aiAdvicePresent"));
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
                    "Circuit breaker should be CLOSED after successful call in HALF_OPEN");
        }

        @Test
        void circuitBreaker_halfOpenPartialFailure_reopens() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(4)
                    .minimumNumberOfCalls(4)
                    .waitDurationInOpenState(Duration.ofMillis(100))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .recordExceptions(TransientAiException.class)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            CircuitBreaker cb = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("cb-half-open-fail");

            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("no-retry-hof");

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new TransientAiException("server error"));

            AiAdvisedDecisionService service = buildService(advisor, cb, noRetry);

            // Drive circuit breaker to OPEN
            for (int i = 0; i < 4; i++) {
                service.decide(createContext());
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            // Transition to HALF_OPEN
            cb.transitionToHalfOpenState();
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

            // Keep advisor failing — half-open calls should fail and reopen the breaker
            service.decide(createContext());
            service.decide(createContext());

            assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                    "Circuit breaker should re-OPEN after failures in HALF_OPEN state");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group B: Retry Behavior
    // ──────────────────────────────────────────────────────────────

    @Nested
    class RetryTests {

        @Test
        void transientException_retriedThenSucceeds() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("retry-success");

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(
                            Duration.ofMillis(10), 2))
                    .retryOnException(e -> e instanceof TransientAiException)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            Retry retry = RetryRegistry.of(retryConfig).retry("retry-success");

            // Fail first 2 calls, succeed on 3rd
            CountingAdvisor advisor = new CountingAdvisor();
            advisor.failNTimesThenSucceed(2, validAdvice());

            AiAdvisedDecisionService service = buildService(advisor, cb, retry);
            DecisionResult result = service.decide(createContext());

            assertEquals(true, result.attributes().get("aiAdvicePresent"),
                    "Should succeed after 2 transient failures + 1 success");
            assertEquals(3, advisor.getCallCount(),
                    "Advisor should be called exactly 3 times (2 failures + 1 success)");
        }

        @Test
        void permanentException_notRetried() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                    .ignoreExceptions(PermanentAiException.class)
                    .build()).circuitBreaker("perm-no-retry");

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(
                            Duration.ofMillis(10), 2))
                    .retryOnException(e -> e instanceof TransientAiException)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            Retry retry = RetryRegistry.of(retryConfig).retry("perm-no-retry");

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new PermanentAiException("bad request"));

            AiAdvisedDecisionService service = buildService(advisor, cb, retry);
            DecisionResult result = service.decide(createContext());

            assertEquals(false, result.attributes().get("aiAdvicePresent"),
                    "Should fall back to deterministic on permanent error");
            assertEquals(1, advisor.getCallCount(),
                    "Advisor should be called exactly 1 time — no retry on permanent error");
        }

        @Test
        void retryExhaustion_fallsBackToDeterministic() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("retry-exhaust");

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(
                            Duration.ofMillis(10), 2))
                    .retryOnException(e -> e instanceof TransientAiException)
                    .ignoreExceptions(PermanentAiException.class, BudgetExceededException.class)
                    .build();
            Retry retry = RetryRegistry.of(retryConfig).retry("retry-exhaust");

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new TransientAiException("always failing"));

            AiAdvisedDecisionService service = buildService(advisor, cb, retry);
            DecisionResult result = service.decide(createContext());

            assertEquals(false, result.attributes().get("aiAdvicePresent"),
                    "Should fall back after retry exhaustion");
            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertEquals(3, advisor.getCallCount(),
                    "Advisor should be called 3 times before giving up");

            double errorCount = countMetric("triagemate.ai.fallback.total", "reason", "error");
            assertTrue(errorCount >= 1, "Expected error fallback metric >= 1, got " + errorCount);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group C: HTTP Error Classification
    // ──────────────────────────────────────────────────────────────

    @Nested
    class HttpErrorClassificationTests {

        private SpringAiDecisionAdvisor advisor;

        @BeforeEach
        void setUpAdvisor() {
            // Create a minimal advisor; we only test classifyException, not advise()
            advisor = new SpringAiDecisionAdvisor(null, null, null, null, properties);
        }

        @Test
        void classifyException_http401_permanent() {
            Exception ex = HttpClientErrorException.create(
                    HttpStatus.UNAUTHORIZED, "Unauthorized",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(PermanentAiException.class, result,
                    "HTTP 401 should be classified as permanent");
        }

        @Test
        void classifyException_http400_permanent() {
            Exception ex = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST, "Bad Request",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(PermanentAiException.class, result,
                    "HTTP 400 should be classified as permanent");
        }

        @Test
        void classifyException_http403_permanent() {
            Exception ex = HttpClientErrorException.create(
                    HttpStatus.FORBIDDEN, "Forbidden",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(PermanentAiException.class, result,
                    "HTTP 403 should be classified as permanent");
        }

        @Test
        void classifyException_http429_transient() {
            Exception ex = HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(TransientAiException.class, result,
                    "HTTP 429 should be classified as transient (eligible for retry)");
        }

        @Test
        void classifyException_http503_transient() {
            Exception ex = HttpServerErrorException.create(
                    HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(TransientAiException.class, result,
                    "HTTP 503 should be classified as transient (eligible for retry)");
        }

        @Test
        void classifyException_http500_transient() {
            Exception ex = HttpServerErrorException.create(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(TransientAiException.class, result,
                    "HTTP 500 should be classified as transient (eligible for retry)");
        }

        @Test
        void classifyException_wrappedHttpException_stillClassified() {
            HttpClientErrorException httpEx = HttpClientErrorException.create(
                    HttpStatus.UNAUTHORIZED, "Unauthorized",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            // Wrap in a RuntimeException to simulate Spring AI wrapping
            Exception wrapped = new RuntimeException("Spring AI error", httpEx);

            AiAdvisoryException result = advisor.classifyException(wrapped);
            assertInstanceOf(PermanentAiException.class, result,
                    "Wrapped HTTP 401 should still be classified as permanent via cause-chain walk");
        }

        @Test
        void classifyException_nonHttp_transient() {
            Exception ex = new RuntimeException("Connection refused");

            AiAdvisoryException result = advisor.classifyException(ex);
            assertInstanceOf(TransientAiException.class, result,
                    "Non-HTTP errors (connection refused, DNS, etc.) should be classified as transient");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group D: Executor & Queue Tests
    // ──────────────────────────────────────────────────────────────

    @Nested
    class ExecutorTests {

        @Test
        void rejectedExecution_fallsBackWithQueueFullMetric() {
            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("queue-full");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("queue-full");

            // Create a tiny executor that will reject tasks immediately
            ThreadPoolExecutor tinyExecutor = new ThreadPoolExecutor(
                    1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.SynchronousQueue<>(),
                    new ThreadPoolExecutor.AbortPolicy()
            );

            // Block the only thread
            java.util.concurrent.CountDownLatch blockLatch = new java.util.concurrent.CountDownLatch(1);
            tinyExecutor.submit(() -> {
                try { blockLatch.await(); } catch (InterruptedException ignored) { }
            });

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setAdvice(validAdvice());

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, advisor,
                    new AiAdviceValidator(properties),
                    stubAudit,
                    new AiCostTracker(properties, meterRegistry),
                    new AiMetrics(meterRegistry),
                    cb, noRetry, tinyExecutor, properties
            );

            DecisionResult result = service.decide(createContext());

            assertEquals(DecisionOutcome.ACCEPT, result.outcome());
            assertEquals(false, result.attributes().get("aiAdvicePresent"),
                    "Should fall back to deterministic when executor queue is full");

            double queueFullCount = countMetric("triagemate.ai.fallback.total", "reason", "queue_full");
            assertTrue(queueFullCount >= 1,
                    "Expected queue_full fallback metric >= 1, got " + queueFullCount);

            // Cleanup
            blockLatch.countDown();
            tinyExecutor.shutdownNow();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group E: Metrics Verification
    // ──────────────────────────────────────────────────────────────

    @Nested
    class MetricsTests {

        @Test
        void timeoutFallback_tracksMetric() {
            AiAdvisoryProperties shortTimeout = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofMillis(20)),
                    new AiAdvisoryProperties.Cost(0.05, 100.0),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            CountingAdvisor slowAdvisor = new CountingAdvisor() {
                @Override
                public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
                    super.advise(context, deterministicResult);
                    try { Thread.sleep(500); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return validAdvice();
                }
            };

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("timeout-metric");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("timeout-metric");

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, slowAdvisor,
                    new AiAdviceValidator(shortTimeout),
                    stubAudit,
                    new AiCostTracker(shortTimeout, meterRegistry),
                    new AiMetrics(meterRegistry),
                    cb, noRetry, Executors.newSingleThreadExecutor(),
                    shortTimeout
            );

            service.decide(createContext());

            double timeoutCount = countMetric("triagemate.ai.fallback.total", "reason", "timeout");
            assertTrue(timeoutCount >= 1,
                    "Expected timeout fallback metric >= 1, got " + timeoutCount);
        }

        @Test
        void errorFallback_tracksMetric() {
            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setThrowException(new TransientAiException("boom"));

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("error-metric");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("error-metric");

            AiAdvisedDecisionService service = buildService(advisor, cb, noRetry);
            service.decide(createContext());

            double errorCount = countMetric("triagemate.ai.fallback.total", "reason", "error");
            assertTrue(errorCount >= 1,
                    "Expected error fallback metric >= 1, got " + errorCount);
        }

        @Test
        void budgetExceededFallback_tracksMetric() {
            AiAdvisoryProperties tightBudget = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                    new AiAdvisoryProperties.Cost(0.05, 0.01, 0.05),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiCostTracker costTracker = new AiCostTracker(tightBudget, meterRegistry);
            costTracker.recordCost(0.02); // exhaust budget

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setAdvice(validAdvice());

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("budget-metric");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("budget-metric");

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, advisor,
                    new AiAdviceValidator(tightBudget),
                    stubAudit, costTracker,
                    new AiMetrics(meterRegistry),
                    cb, noRetry, Executors.newSingleThreadExecutor(),
                    tightBudget
            );

            service.decide(createContext());

            double budgetCount = countMetric("triagemate.ai.fallback.total", "reason", "budget_exceeded");
            assertTrue(budgetCount >= 1,
                    "Expected budget_exceeded fallback metric >= 1, got " + budgetCount);
            assertEquals(0, advisor.getCallCount(),
                    "AI advisor should NOT be called when budget is exceeded");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group F: Concurrent Fallback Safety
    // ──────────────────────────────────────────────────────────────

    @Nested
    class ConcurrentFallbackTests {

        @Test
        void concurrentCalls_allFallBackSafely() throws Exception {
            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("concurrent");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("concurrent");

            CountingAdvisor slowFailingAdvisor = new CountingAdvisor() {
                @Override
                public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
                    super.advise(context, deterministicResult);
                    try { Thread.sleep(50); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new TransientAiException("slow failure");
                }
            };

            AiAdvisoryProperties shortTimeout = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofMillis(20)),
                    new AiAdvisoryProperties.Cost(0.05, 100.0),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, slowFailingAdvisor,
                    new AiAdviceValidator(shortTimeout),
                    stubAudit,
                    new AiCostTracker(shortTimeout, meterRegistry),
                    new AiMetrics(meterRegistry),
                    cb, noRetry,
                    Executors.newFixedThreadPool(4),
                    shortTimeout
            );

            int threadCount = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ConcurrentLinkedQueue<DecisionResult> results = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        results.add(service.decide(createContext()));
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown(); // release all threads at once
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete within 10s");
            pool.shutdownNow();

            assertTrue(errors.isEmpty(), "No thread should throw an uncaught exception: " + errors);
            assertEquals(threadCount, results.size(), "All threads should produce a result");

            for (DecisionResult r : results) {
                assertEquals(DecisionOutcome.ACCEPT, r.outcome(),
                        "Every concurrent call should fall back to deterministic ACCEPT");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group G: MDC Propagation to AI Executor Threads
    // ──────────────────────────────────────────────────────────────

    @Nested
    class MdcPropagationTests {

        @Test
        void mdcContext_propagatedToAiExecutorThread() {
            AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

            AiDecisionAdvisor mdcCapturingAdvisor = (context, deterministicResult) -> {
                capturedMdc.set(MDC.getCopyOfContextMap());
                return validAdvice();
            };

            // Use a ThreadPoolTaskExecutor with MdcTaskDecorator (same as production config)
            org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                    new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.setTaskDecorator(new com.triagemate.triage.config.MdcTaskDecorator());
            executor.initialize();

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("mdc-test");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("mdc-test");

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, mdcCapturingAdvisor,
                    new AiAdviceValidator(properties),
                    stubAudit,
                    new AiCostTracker(properties, meterRegistry),
                    new AiMetrics(meterRegistry),
                    cb, noRetry, executor, properties
            );

            // Set MDC on the calling thread
            MDC.put("traceId", "trace-abc-123");
            MDC.put("eventId", "evt-mdc-test");
            try {
                DecisionResult result = service.decide(createContext());
                assertEquals(true, result.attributes().get("aiAdvicePresent"));

                assertNotNull(capturedMdc.get(), "MDC context should be captured by AI thread");
                assertEquals("trace-abc-123", capturedMdc.get().get("traceId"),
                        "traceId should propagate from caller to AI executor thread");
                assertEquals("evt-mdc-test", capturedMdc.get().get("eventId"),
                        "eventId should propagate from caller to AI executor thread");
            } finally {
                MDC.clear();
                executor.shutdown();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group H: Cost Accumulation & Reset Lifecycle
    // ──────────────────────────────────────────────────────────────

    @Nested
    class CostTrackingTests {

        @Test
        void costAccumulation_acrossMultipleDecisions() {
            // checkBudget uses estimatedCostUsd ($0.05),
            // so daily check is: accumulated + $0.05 > maxDailyUsd
            AiAdvisoryProperties costProps = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                    new AiAdvisoryProperties.Cost(0.05, 0.12, 0.05),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiCostTracker costTracker = new AiCostTracker(costProps, meterRegistry);

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setAdvice(new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.92, "match", true,
                    "test", "model", "v1", "1.0.0", "hash",
                    10, 20, 0.03, 50 // $0.03 actual per call
            ));

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("cost-accumulate");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("cost-accumulate");

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, advisor,
                    new AiAdviceValidator(costProps),
                    stubAudit, costTracker,
                    new AiMetrics(meterRegistry),
                    cb, noRetry, Executors.newSingleThreadExecutor(), costProps
            );

            // Call 1: budget check → 0.00 + 0.05 = 0.05 < 0.12 → pass, actual cost $0.03
            DecisionResult r1 = service.decide(createContext());
            assertEquals(true, r1.attributes().get("aiAdvicePresent"));
            assertEquals(0.03, costTracker.getDailyCostUsd(), 0.001);

            // Call 2: budget check → 0.03 + 0.05 = 0.08 < 0.12 → pass, daily = $0.06
            DecisionResult r2 = service.decide(createContext());
            assertEquals(true, r2.attributes().get("aiAdvicePresent"));
            assertEquals(0.06, costTracker.getDailyCostUsd(), 0.001);

            // Call 3: budget check → 0.06 + 0.05 = 0.11 < 0.12 → pass, daily = $0.09
            DecisionResult r3 = service.decide(createContext());
            assertEquals(true, r3.attributes().get("aiAdvicePresent"));
            assertEquals(0.09, costTracker.getDailyCostUsd(), 0.001);

            // Call 4: budget check → 0.09 + 0.05 = 0.14 > 0.12 → budget exceeded → fallback
            DecisionResult r4 = service.decide(createContext());
            assertEquals(false, r4.attributes().get("aiAdvicePresent"),
                    "Fourth call should fall back because estimated cost would exceed daily budget");
        }

        @Test
        void dailyCostReset_allowsNewCallsAfterReset() {
            // checkBudget uses estimatedCostUsd ($0.05),
            // so daily check is: accumulated + $0.05 > maxDailyUsd ($0.07)
            AiAdvisoryProperties costProps = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                    new AiAdvisoryProperties.Cost(0.05, 0.07, 0.05),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiCostTracker costTracker = new AiCostTracker(costProps, meterRegistry);

            CountingAdvisor advisor = new CountingAdvisor();
            advisor.setAdvice(new AiDecisionAdvice(
                    "DEVICE_ERROR", 0.92, "match", true,
                    "test", "model", "v1", "1.0.0", "hash",
                    10, 20, 0.03, 50 // $0.03 actual per call
            ));

            CircuitBreaker cb = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                    .circuitBreaker("cost-reset");
            Retry noRetry = RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(1).build()).retry("cost-reset");

            AiAdvisedDecisionService service = new AiAdvisedDecisionService(
                    stubDelegate, advisor,
                    new AiAdviceValidator(costProps),
                    stubAudit, costTracker,
                    new AiMetrics(meterRegistry),
                    cb, noRetry, Executors.newSingleThreadExecutor(), costProps
            );

            // Call 1: budget check → 0.00 + 0.05 = 0.05 < 0.07 → pass, daily = $0.03
            DecisionResult r1 = service.decide(createContext());
            assertEquals(true, r1.attributes().get("aiAdvicePresent"));

            // Call 2: budget check → 0.03 + 0.05 = 0.08 > 0.07 → budget exceeded
            DecisionResult r2 = service.decide(createContext());
            assertEquals(false, r2.attributes().get("aiAdvicePresent"));

            // Reset daily cost (simulates midnight rollover)
            costTracker.resetDailyCost();
            assertEquals(0.0, costTracker.getDailyCostUsd(), 0.001);

            // Call 3: budget check → 0.00 + 0.05 = 0.05 < 0.07 → pass again
            DecisionResult r3 = service.decide(createContext());
            assertEquals(true, r3.attributes().get("aiAdvicePresent"),
                    "After daily cost reset, AI calls should be permitted again");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Group I: Cost Tracker Concurrency Safety
    // ──────────────────────────────────────────────────────────────

    @Nested
    class CostTrackerConcurrencyTests {

        @Test
        void concurrentCheckAndRecord_neverExceedsBudget() throws Exception {
            // Budget: $0.05 per decision, $0.50 daily, estimate $0.05
            // Each call costs $0.05, so max 10 calls allowed
            AiAdvisoryProperties costProps = new AiAdvisoryProperties(
                    true, "test", null, null,
                    Set.of("DEVICE_ERROR", "NORMAL"),
                    new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                    new AiAdvisoryProperties.Cost(0.05, 0.50, 0.05),
                    new AiAdvisoryProperties.Validation(0.70, 0.85)
            );

            AiCostTracker costTracker = new AiCostTracker(costProps, meterRegistry);

            int threadCount = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger budgetExceededCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        costTracker.checkBudget(0.05);
                        costTracker.recordCost(0.05);
                        successCount.incrementAndGet();
                    } catch (BudgetExceededException e) {
                        budgetExceededCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
            pool.shutdownNow();

            assertEquals(threadCount, successCount.get() + budgetExceededCount.get(),
                    "Every thread should either succeed or get BudgetExceededException");

            // With $0.50 budget and $0.05 per call, max 10 successful calls.
            // The daily cost should never exceed the budget.
            assertTrue(costTracker.getDailyCostUsd() <= 0.50 + 0.001,
                    "Daily cost (" + costTracker.getDailyCostUsd() + ") must not exceed budget ($0.50)");
            assertTrue(successCount.get() <= 10,
                    "At most 10 calls should succeed with $0.50 budget at $0.05 each, got " + successCount.get());
            assertTrue(budgetExceededCount.get() >= 10,
                    "At least 10 of 20 threads should be rejected, got " + budgetExceededCount.get());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private AiAdvisedDecisionService buildService(CountingAdvisor advisor, CircuitBreaker cb, Retry retry) {
        return new AiAdvisedDecisionService(
                stubDelegate, advisor,
                new AiAdviceValidator(properties),
                stubAudit,
                new AiCostTracker(properties, meterRegistry),
                new AiMetrics(meterRegistry),
                cb, retry,
                Executors.newSingleThreadExecutor(),
                properties
        );
    }

    private DecisionContext<?> createContext() {
        return DecisionContext.of(
                "evt-123", "device.telemetry", 1,
                Instant.now(), Map.of(), "test-payload"
        );
    }

    private AiDecisionAdvice validAdvice() {
        return new AiDecisionAdvice(
                "DEVICE_ERROR", 0.92, "High confidence match", true,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 50
        );
    }

    private double countMetric(String name, String tagKey, String tagValue) {
        Counter counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? counter.count() : 0.0;
    }

    /**
     * Advisor stub that counts invocations and supports transient failure injection
     * with configurable recovery.
     */
    static class CountingAdvisor implements AiDecisionAdvisor {
        private AiDecisionAdvice advice = AiDecisionAdvice.NONE;
        private RuntimeException throwException;
        private final AtomicInteger callCount = new AtomicInteger(0);
        private int failNTimes = -1; // -1 means use throwException mode
        private AiDecisionAdvice successAdvice;

        void setAdvice(AiDecisionAdvice advice) { this.advice = advice; }

        void setThrowException(RuntimeException e) {
            this.throwException = e;
            this.failNTimes = -1;
        }

        void failNTimesThenSucceed(int n, AiDecisionAdvice successAdvice) {
            this.failNTimes = n;
            this.successAdvice = successAdvice;
            this.throwException = null;
        }

        int getCallCount() { return callCount.get(); }

        @Override
        public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
            int currentCall = callCount.incrementAndGet();

            if (failNTimes >= 0) {
                if (currentCall <= failNTimes) {
                    throw new TransientAiException("transient failure #" + currentCall);
                }
                return successAdvice;
            }

            if (throwException != null) throw throwException;
            return advice;
        }
    }
}
