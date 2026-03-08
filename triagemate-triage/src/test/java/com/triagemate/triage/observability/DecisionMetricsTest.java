package com.triagemate.triage.observability;

import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionMetricsTest {

    private SimpleMeterRegistry registry;
    private DecisionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new DecisionMetrics(registry);
    }

    @Test
    void recordDecision_shouldIncrementCounterWithCorrectTag() {
        metrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(50));

        double count = registry.counter("triagemate.decision.total", "outcome", "accept")
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordDecision_shouldRecordLatency() {
        metrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(123));

        double totalTime = registry.timer("triagemate.decision.latency.seconds",
                        "outcome", "accept")
                .totalTime(TimeUnit.MILLISECONDS);
        assertThat(totalTime).isEqualTo(123.0);
    }

    @Test
    void recordDecision_shouldTrackMultipleOutcomes() {
        metrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(10));
        metrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(20));
        metrics.recordDecision(DecisionOutcome.REJECT, Duration.ofMillis(5));

        assertThat(registry.counter("triagemate.decision.total", "outcome", "accept").count())
                .isEqualTo(2.0);
        assertThat(registry.counter("triagemate.decision.total", "outcome", "reject").count())
                .isEqualTo(1.0);
    }

    @Test
    void recordInvalid_shouldIncrementInvalidCounter() {
        metrics.recordInvalid();
        metrics.recordInvalid();

        double count = registry.counter("triagemate.decision.invalid.total").count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    void recordError_shouldIncrementErrorCounter() {
        metrics.recordError();

        double count = registry.counter("triagemate.decision.error.total").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void timeDecision_shouldRecordSuccessMetrics() {
        DecisionResult expected = DecisionResult.of(
                DecisionOutcome.ACCEPT, "Valid", Map.of());

        DecisionResult actual = metrics.timeDecision(() -> expected);

        assertThat(actual).isEqualTo(expected);
        assertThat(registry.counter("triagemate.decision.total", "outcome", "accept")
                .count()).isEqualTo(1.0);
        assertThat(registry.timer("triagemate.decision.latency.seconds", "outcome", "accept")
                .count()).isEqualTo(1);
    }

    @Test
    void timeDecision_shouldRecordErrorOnException() {
        assertThatThrownBy(() ->
                metrics.timeDecision(() -> {
                    throw new RuntimeException("Test failure");
                })
        ).isInstanceOf(RuntimeException.class);

        assertThat(registry.counter("triagemate.decision.error.total").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("triagemate.decision.total", "outcome", "accept")
                .count()).isEqualTo(0.0);
    }

    @Test
    void timeDecision_shouldRecordPositiveLatency() {
        DecisionResult result = DecisionResult.of(
                DecisionOutcome.REJECT, "Policy rejected", Map.of());

        metrics.timeDecision(() -> result);

        double totalTime = registry.timer("triagemate.decision.latency.seconds",
                        "outcome", "reject")
                .totalTime(TimeUnit.NANOSECONDS);
        assertThat(totalTime).isGreaterThan(0);
    }
}
