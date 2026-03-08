package com.triagemate.triage.observability;

import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class MetricsExposureTest extends KafkaIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DecisionMetrics decisionMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void meterRegistry_shouldBePrometheusBacked() {
        // Diagnostic: verify the registry type injected by Spring Boot
        String registryClass = meterRegistry.getClass().getName();
        assertThat(registryClass)
                .as("MeterRegistry should be Prometheus-backed, got: " + registryClass
                        + ". Check micrometer-registry-prometheus dependency.")
                .containsIgnoringCase("prometheus");
    }

    @Test
    void prometheusEndpoint_shouldBeAccessible() {
        // Diagnostic: check what endpoints are actually exposed
        ResponseEntity<String> actuatorResponse = restTemplate.getForEntity(
                "/actuator", String.class);
        assertThat(actuatorResponse.getStatusCode())
                .as("Actuator root should be accessible. Body: " + actuatorResponse.getBody())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode())
                .as("Prometheus endpoint returned " + response.getStatusCode()
                        + ". Body: " + response.getBody()
                        + ". MeterRegistry: " + meterRegistry.getClass().getName()
                        + ". Actuator links: " + actuatorResponse.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("text/plain");
    }

    @Test
    void prometheusEndpoint_shouldExposeDecisionMetricsAfterRecording() {
        decisionMetrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(50));
        decisionMetrics.recordDecision(DecisionOutcome.REJECT, Duration.ofMillis(30));
        decisionMetrics.recordInvalid();
        decisionMetrics.recordError();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("triagemate_decision_total");
        assertThat(body).contains("outcome=\"accept\"");
        assertThat(body).contains("outcome=\"reject\"");
        assertThat(body).contains("triagemate_decision_invalid_total");
        assertThat(body).contains("triagemate_decision_error_total");
        assertThat(body).contains("triagemate_decision_latency_seconds");
        assertThat(body).contains("application=\"triagemate-triage\"");
    }

    @Test
    void prometheusEndpoint_shouldExposeHistogramBuckets() {
        decisionMetrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(25));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("triagemate_decision_latency_seconds_bucket");
        assertThat(body).contains("triagemate_decision_latency_seconds_count");
        assertThat(body).contains("triagemate_decision_latency_seconds_sum");
    }

    @Test
    void timeDecision_shouldExposeMetricsViaPrometheus() {
        decisionMetrics.timeDecision(() ->
                DecisionResult.of(DecisionOutcome.DEFER, "deferred", Map.of()));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("outcome=\"defer\"");
    }

    @Test
    void prometheusEndpoint_shouldExposeOutboxMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).isNotNull();

        // Outbox counters (eagerly registered, visible at 0.0)
        assertThat(body).contains("triagemate_outbox_published_total");
        assertThat(body).contains("triagemate_outbox_retry_total");
        assertThat(body).contains("triagemate_kafka_publish_failure_total");
        assertThat(body).contains("triagemate_outbox_validation_failure_total");

        // Outbox gauges
        assertThat(body).contains("triagemate_outbox_pending_count");
        assertThat(body).contains("triagemate_outbox_oldest_message_age_seconds");
    }
}
