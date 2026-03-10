package com.triagemate.triage.health;

import com.triagemate.triage.persistence.JdbcOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class OutboxBacklogHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OutboxBacklogHealthIndicator.class);

    public static final Status DEGRADED = new Status("DEGRADED");

    private static final long WARN_THRESHOLD = 100L;
    private static final long CRITICAL_THRESHOLD = 500L;

    private final JdbcOutboxRepository repository;

    public OutboxBacklogHealthIndicator(JdbcOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        long pending = repository.countPending();
        double oldestAgeSeconds = repository.oldestPendingAgeSeconds();

        if (pending > CRITICAL_THRESHOLD) {
            log.error("Outbox backlog critical: pending={} oldestMessageAgeSeconds={}", pending, oldestAgeSeconds);
            return withCommonDetails(Health.down(), pending, oldestAgeSeconds)
                    .withDetail("status", "Backlog critical")
                    .build();
        }

        if (pending > WARN_THRESHOLD) {
            log.warn("Outbox backlog degraded: pending={} oldestMessageAgeSeconds={}", pending, oldestAgeSeconds);
            return withCommonDetails(Health.status(DEGRADED), pending, oldestAgeSeconds)
                    .withDetail("status", "Backlog degraded")
                    .build();
        }

        return withCommonDetails(Health.up(), pending, oldestAgeSeconds)
                .withDetail("status", "Backlog healthy")
                .build();
    }

    private Health.Builder withCommonDetails(Health.Builder builder, long pending, double oldestAgeSeconds) {
        return builder
                .withDetail("pendingCount", pending)
                .withDetail("oldestMessageAgeSeconds", oldestAgeSeconds)
                .withDetail("warnThreshold", WARN_THRESHOLD)
                .withDetail("criticalThreshold", CRITICAL_THRESHOLD);
    }
}
