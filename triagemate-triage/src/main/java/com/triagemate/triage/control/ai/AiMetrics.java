package com.triagemate.triage.control.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCall(String provider, String status, long latencyMs) {
        Counter.builder("triagemate.ai.calls.total")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("status", status)
                .register(registry)
                .increment();

        Timer.builder("triagemate.ai.latency.seconds")
                .tag("provider", provider != null ? provider : "unknown")
                .register(registry)
                .record(Duration.ofMillis(latencyMs));
    }

    public void recordCost(String provider, double costUsd) {
        Counter.builder("triagemate.ai.cost.usd.total")
                .tag("provider", provider != null ? provider : "unknown")
                .register(registry)
                .increment(costUsd);
    }

    public void recordAdviceAccepted() {
        Counter.builder("triagemate.ai.advice.accepted.total")
                .register(registry)
                .increment();
    }

    public void recordAdviceRejected() {
        Counter.builder("triagemate.ai.advice.rejected.total")
                .register(registry)
                .increment();
    }

    public void recordFallback(String reason) {
        Counter.builder("triagemate.ai.fallback.total")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
