package com.triagemate.triage.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void decorated_task_inherits_mdc_from_parent() throws Exception {
        MDC.put("requestId", "req-123");
        MDC.put("correlationId", "corr-456");

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.getCopyOfContextMap()));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(captured.get()).containsEntry("requestId", "req-123");
        assertThat(captured.get()).containsEntry("correlationId", "corr-456");
    }

    @Test
    void decorated_task_clears_mdc_after_execution() throws Exception {
        MDC.put("requestId", "req-123");

        AtomicReference<Map<String, String>> afterExecution = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            // task runs
        });

        Thread thread = new Thread(() -> {
            decorated.run();
            afterExecution.set(MDC.getCopyOfContextMap());
        });
        thread.start();
        thread.join();

        assertThat(afterExecution.get()).isNullOrEmpty();
    }

    @Test
    void decorated_task_handles_null_parent_mdc() throws Exception {
        MDC.clear();

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> captured.set(MDC.getCopyOfContextMap()));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(captured.get()).isNullOrEmpty();
    }

    @Test
    void decorated_task_clears_stale_mdc_from_reused_thread() throws Exception {
        // Parent has no MDC — decorator captures null contextMap
        MDC.clear();
        Runnable decorated = decorator.decorate(() -> {
            // no-op
        });

        AtomicReference<Map<String, String>> capturedDuringRun = new AtomicReference<>();

        // Simulate thread reuse: thread already has stale MDC from a previous task
        Thread thread = new Thread(() -> {
            MDC.put("requestId", "stale-req");
            MDC.put("correlationId", "stale-corr");

            // Now run the decorated task (parent had no MDC)
            // The decorator should clear stale values before running
            Runnable inspecting = decorator.decorate(() ->
                    capturedDuringRun.set(MDC.getCopyOfContextMap()));
            inspecting.run();
        });
        thread.start();
        thread.join();

        // Stale MDC should NOT be visible during task execution
        assertThat(capturedDuringRun.get()).isNullOrEmpty();
    }
}
