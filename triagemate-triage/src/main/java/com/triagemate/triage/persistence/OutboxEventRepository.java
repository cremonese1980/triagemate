package com.triagemate.triage.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // No custom queries here.
    // Write-only repository for transactional insert.
}
