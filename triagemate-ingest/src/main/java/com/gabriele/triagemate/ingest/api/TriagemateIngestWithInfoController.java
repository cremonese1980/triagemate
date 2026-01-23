package com.gabriele.triagemate.ingest.api;

import com.gabriele.triagemate.ingest.client.dto.TriagemateTriageResponse;
import com.gabriele.triagemate.ingest.dto.TriagemateIngestWithInfoResponse;
import com.gabriele.triagemate.ingest.dto.TriagemateTriageStatus;
import com.gabriele.triagemate.ingest.dto.TriagemateTriageStatusKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class TriagemateIngestWithInfoController {

    private static final Logger log = LoggerFactory.getLogger(TriagemateIngestWithInfoController.class);

    private final RestClient infoRestClient;
    private final int localPort;
    private final int timeoutMs;

    public TriagemateIngestWithInfoController(
            RestClient infoRestClient,
            @Value("${server.port}") int localPort,
            @Value("${triagemate-triage.timeout-ms}") int timeoutMs
    ) {
        this.infoRestClient = infoRestClient;
        this.localPort = localPort;
        this.timeoutMs = timeoutMs;
    }

    @GetMapping("/hello-with-info-strict")
    public ResponseEntity<TriagemateIngestWithInfoResponse> strict() {
        return callInfoService(DownstreamPolicy.STRICT);
    }

    @GetMapping("/hello-with-info-best-effort")
    public ResponseEntity<TriagemateIngestWithInfoResponse> bestEffort() {
        return callInfoService(DownstreamPolicy.BEST_EFFORT);
    }

    private ResponseEntity<TriagemateIngestWithInfoResponse> callInfoService(DownstreamPolicy policy) {

        var startNs = System.nanoTime();
        TriagemateTriageStatus infoStatus;

        try {
            var payload = infoRestClient.get()
                    .uri("/api/info/slow?ms=500")
                    .retrieve()
                    .body(TriagemateTriageResponse.class);

            var latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            infoStatus = TriagemateTriageStatus.ok( "triagemate-ingest", payload, latencyMs);

        } catch (ResourceAccessException ex) {
            var latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            var msg = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();

            infoStatus = isTimeout(ex)
                    ? TriagemateTriageStatus.timeout("triagemate-triage", msg, latencyMs)
                    : TriagemateTriageStatus.unavailable("triagemate-triage", msg, latencyMs);
        }

        var response = new TriagemateIngestWithInfoResponse(
                "triagemate-ingest",
                localPort,
                Instant.now(),
                timeoutMs,
                infoStatus
        );

        var httpStatus = switch (policy) {
            case STRICT -> (infoStatus.kind() .equals(TriagemateTriageStatusKind.OK))
                    ? HttpStatus.OK
                    : HttpStatus.SERVICE_UNAVAILABLE;
            case BEST_EFFORT -> HttpStatus.OK;
        };

        log.info("hello service");

        return ResponseEntity.status(httpStatus).body(response);
    }

    private enum DownstreamPolicy {
        STRICT,
        BEST_EFFORT
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.http.HttpTimeoutException) return true;
            if (t instanceof java.net.SocketTimeoutException) return true;
            if (t instanceof java.io.InterruptedIOException) return true; // common for timeouts
            if (t instanceof java.util.concurrent.TimeoutException) return true;
        }
        return false;
    }

}
