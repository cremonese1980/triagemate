package com.triagemate.triage.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, UUID> {

    Optional<DecisionRecord> findByEventId(String eventId);

    List<DecisionRecord> findByPolicyVersion(String policyVersion);
}
