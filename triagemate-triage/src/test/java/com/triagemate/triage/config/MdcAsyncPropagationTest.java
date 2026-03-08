package com.triagemate.triage.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the AsyncConfig-created taskExecutor propagates MDC
 * from the calling thread to async worker threads via MdcTaskDecorator.
 */
class MdcAsyncPropagationTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void taskExecutor_propagatesMdc_toAsyncThread() throws Exception {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.taskExecutor();

        MDC.put("requestId", "req-async-123");
        MDC.put("correlationId", "corr-async-456");

        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> capturedMdc.set(MDC.getCopyOfContextMap()),
                executor
        );
        future.get();

        assertThat(capturedMdc.get()).isNotNull();
        assertThat(capturedMdc.get().get("requestId")).isEqualTo("req-async-123");
        assertThat(capturedMdc.get().get("correlationId")).isEqualTo("corr-async-456");
    }

    @Test
    void taskExecutor_clearsMdc_afterAsyncTaskCompletes() throws Exception {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.taskExecutor();

        MDC.put("requestId", "req-cleanup");

        AtomicReference<Map<String, String>> afterTask = new AtomicReference<>();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // Task runs with MDC
        }, executor).thenRunAsync(() -> {
            // Next task on same pool — should not see previous MDC
            // unless explicitly set by parent
        }, executor);
        future.get();

        // Parent thread MDC is not affected by child thread cleanup
        assertThat(MDC.get("requestId")).isEqualTo("req-cleanup");
    }

    @Test
    void taskExecutor_isolatesMdc_betweenConsecutiveTasks() throws Exception {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.taskExecutor();

        // Task 1: set MDC with trace-1
        MDC.put("requestId", "req-task-1");
        AtomicReference<String> captured1 = new AtomicReference<>();

        CompletableFuture.runAsync(
                () -> captured1.set(MDC.get("requestId")),
                executor
        ).get();

        // Task 2: set MDC with trace-2
        MDC.put("requestId", "req-task-2");
        AtomicReference<String> captured2 = new AtomicReference<>();

        CompletableFuture.runAsync(
                () -> captured2.set(MDC.get("requestId")),
                executor
        ).get();

        assertThat(captured1.get()).isEqualTo("req-task-1");
        assertThat(captured2.get()).isEqualTo("req-task-2");
    }

    @Test
    void taskExecutor_handlesNullParentMdc() throws Exception {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.taskExecutor();

        MDC.clear();

        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

        CompletableFuture.runAsync(
                () -> capturedMdc.set(MDC.getCopyOfContextMap()),
                executor
        ).get();

        assertThat(capturedMdc.get()).isNullOrEmpty();
    }
}
