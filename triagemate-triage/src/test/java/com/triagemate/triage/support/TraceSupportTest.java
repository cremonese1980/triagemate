package com.triagemate.triage.support;

import com.triagemate.contracts.events.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSupportTest {

    @Test
    void requestIdOrGenerate_returnsRequestId_whenTracePresent() {
        var envelope = envelopeWithTrace("req-1", "corr-1");
        assertThat(TraceSupport.requestIdOrGenerate(envelope)).isEqualTo("req-1");
    }

    @Test
    void requestIdOrGenerate_generatesFallback_whenTraceNull() {
        var envelope = envelopeWithoutTrace();
        assertThat(TraceSupport.requestIdOrGenerate(envelope)).startsWith("generated-");
    }

    @Test
    void requestIdOrGenerate_generatesFallback_whenRequestIdNull() {
        var envelope = envelopeWithTrace(null, "corr-1");
        assertThat(TraceSupport.requestIdOrGenerate(envelope)).startsWith("generated-");
    }

    @Test
    void correlationIdOrGenerate_returnsCorrelationId_whenTracePresent() {
        var envelope = envelopeWithTrace("req-1", "corr-1");
        assertThat(TraceSupport.correlationIdOrGenerate(envelope)).isEqualTo("corr-1");
    }

    @Test
    void correlationIdOrGenerate_generatesFallback_whenTraceNull() {
        var envelope = envelopeWithoutTrace();
        assertThat(TraceSupport.correlationIdOrGenerate(envelope)).startsWith("generated-");
    }

    @Test
    void correlationIdOrGenerate_generatesFallback_whenCorrelationIdNull() {
        var envelope = envelopeWithTrace("req-1", null);
        assertThat(TraceSupport.correlationIdOrGenerate(envelope)).startsWith("generated-");
    }

    @Test
    void eventIdOrDefault_returnsEventId_whenPresent() {
        var envelope = envelopeWithTrace("req-1", "corr-1");
        assertThat(TraceSupport.eventIdOrDefault(envelope)).isEqualTo("evt-1");
    }

    @Test
    void eventIdOrDefault_returnsUnknown_whenNull() {
        var envelope = new EventEnvelope<>(
                null, "type", 1, Instant.now(), null, null, "payload", Map.of()
        );
        assertThat(TraceSupport.eventIdOrDefault(envelope)).isEqualTo("unknown");
    }

    @Test
    void generatedFallbacks_areUnique() {
        var envelope = envelopeWithoutTrace();
        String first = TraceSupport.requestIdOrGenerate(envelope);
        String second = TraceSupport.requestIdOrGenerate(envelope);
        assertThat(first).isNotEqualTo(second);
    }

    private EventEnvelope<String> envelopeWithTrace(String requestId, String correlationId) {
        return new EventEnvelope<>(
                "evt-1", "type", 1, Instant.now(), null,
                new EventEnvelope.Trace(requestId, correlationId, null),
                "payload", Map.of()
        );
    }

    private EventEnvelope<String> envelopeWithoutTrace() {
        return new EventEnvelope<>(
                "evt-1", "type", 1, Instant.now(), null,
                null,
                "payload", Map.of()
        );
    }
}
