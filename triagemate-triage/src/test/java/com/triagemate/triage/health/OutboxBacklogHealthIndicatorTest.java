package com.triagemate.triage.health;

import com.triagemate.triage.persistence.JdbcOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxBacklogHealthIndicatorTest {

    private JdbcOutboxRepository repository;
    private OutboxBacklogHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        repository = mock(JdbcOutboxRepository.class);
        indicator = new OutboxBacklogHealthIndicator(repository);
    }

    @Test
    void health_shouldReturnUp_whenPendingBelowWarnThreshold() {
        when(repository.countPending()).thenReturn(50L);
        when(repository.oldestPendingAgeSeconds()).thenReturn(5.0);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pendingCount", 50L);
    }

    @Test
    void health_shouldReturnDegraded_whenPendingAboveWarnThreshold() {
        when(repository.countPending()).thenReturn(101L);
        when(repository.oldestPendingAgeSeconds()).thenReturn(30.0);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(OutboxBacklogHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("pendingCount", 101L);
    }

    @Test
    void health_shouldReturnDown_whenPendingAboveCriticalThreshold() {
        when(repository.countPending()).thenReturn(501L);
        when(repository.oldestPendingAgeSeconds()).thenReturn(300.0);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("pendingCount", 501L);
    }
}
