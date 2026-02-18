package com.triagemate.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triagemate.kafka")
public record TriagemateKafkaProperties(
        Topics topics,
        Consumer consumer
) {
    public record Topics(
            String inputReceived,
            String decisionMade
    ) {}

    public record Consumer(
            String groupId,
            Retry retry
    ) {}

    public record Retry(
            long backoffMs,
            int maxRetries
    ) {}
}
