package com.triagemate.ingest.app;

import java.time.Instant;
import java.util.UUID;

public interface IngestEventPublisher {

    PublishResult publishMessageIngested(String requestId, IngestPayload payload);

    record IngestPayload(String channel, String content, Instant receivedAt) {}

    record PublishResult(UUID messageId, UUID eventId, Instant publishedAt) {}
}
