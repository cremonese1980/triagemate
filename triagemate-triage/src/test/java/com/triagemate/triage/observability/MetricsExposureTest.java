package com.triagemate.triage.observability;

import com.triagemate.testsupport.KafkaIntegrationTestBase;
import com.triagemate.triage.control.decision.DecisionOutcome;
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
    void prometheusEndpoint_shouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("text/plain");
    }

    @Test
    void prometheusEndpoint_shouldExposeDecisionMetricsAfterRecording() {
        // Trigger some metrics
        decisionMetrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(50));
        decisionMetrics.recordDecision(DecisionOutcome.REJECT, Duration.ofMillis(30));
        decisionMetrics.recordInvalid();
        decisionMetrics.recordError();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).isNotNull();

        // Decision total counter
        assertThat(body).contains("triagemate_decision_total");
        assertThat(body).contains("outcome=\"accept\"");
        assertThat(body).contains("outcome=\"reject\"");

        // Invalid counter
        assertThat(body).contains("triagemate_decision_invalid_total");

        // Error counter
        assertThat(body).contains("triagemate_decision_error_total");

        // Latency histogram
        assertThat(body).contains("triagemate_decision_latency_seconds");

        // Global tags
        assertThat(body).contains("application=\"triagemate-triage\"");
    }

    @Test
    void prometheusEndpoint_shouldExposeHistogramBuckets() {
        decisionMetrics.recordDecision(DecisionOutcome.ACCEPT, Duration.ofMillis(25));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        String body = response.getBody();
        assertThat(body).isNotNull();

        // Verify histogram bucket structure
        assertThat(body).contains("triagemate_decision_latency_seconds_bucket");
        assertThat(body).contains("triagemate_decision_latency_seconds_count");
        assertThat(body).contains("triagemate_decision_latency_seconds_sum");
    }

    @Test
    void timeDecision_shouldExposeMetricsViaPrometheus() {
        decisionMetrics.timeDecision(() ->
                com.triagemate.triage.control.decision.DecisionResult.of(
                        DecisionOutcome.DEFER, "deferred", java.util.Map.of()));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("outcome=\"defer\"");
    }
}
