package com.triagemate.ingest.api;

import com.triagemate.ingest.app.IngestEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest")
public class TriagemateIngestController {

    private final IngestEventPublisher publisher;

    public TriagemateIngestController(IngestEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/messages")
    public ResponseEntity<IngestMessageResponse> ingest(
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader,
            @Valid @RequestBody IngestMessageRequest body
    ) {
        String requestId = (requestIdHeader == null || requestIdHeader.isBlank())
                ? "req-" + UUID.randomUUID()
                : requestIdHeader.trim();

        Instant receivedAt = body.receivedAt() != null ? body.receivedAt() : Instant.now();

        var result = publisher.publishMessageIngested(
                requestId,
                new IngestEventPublisher.IngestPayload(
                        body.channel().trim(),
                        body.content(),
                        receivedAt
                )
        );

        var response = new IngestMessageResponse(
                result.messageId(),
                result.eventId(),
                requestId,
                result.publishedAt()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    public record IngestMessageRequest(
            @NotBlank String channel,
            @NotBlank String content,
            Instant receivedAt
    ) {}

    public record IngestMessageResponse(
            UUID messageId,
            UUID eventId,
            String requestId,
            Instant publishedAt
    ) {}
}
