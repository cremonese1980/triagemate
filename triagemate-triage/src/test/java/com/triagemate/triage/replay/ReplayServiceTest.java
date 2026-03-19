package com.triagemate.triage.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.config.ObjectMapperConfig;
import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.DecisionService;
import com.triagemate.triage.observability.DecisionMetrics;
import com.triagemate.triage.persistence.DecisionRecord;
import com.triagemate.triage.persistence.DecisionRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayServiceTest {

    private final DecisionRecordRepository repository = mock(DecisionRecordRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ReplayService replayService;

    @BeforeEach
    void setUp() {
        DecisionService deterministicService = new DecisionService() {
            @Override
            public DecisionResult decide(DecisionContext<?> context) {
                return DecisionResult.of(
                        DecisionOutcome.ACCEPT,
                        "DEVICE_ERROR",
                        Map.of("decisionId", UUID.randomUUID().toString(), "strategy", "rules-v1"),
                        null,
                        "AI override replayed as deterministic outcome",
                        "1.0.0"
                );
            }
        };

        replayService = new ReplayService(
                repository,
                deterministicService,
                objectMapper,
                new DecisionMetrics(meterRegistry)
        );
    }

    @Test
    void replayByDecisionId_preservesAiAttributesAndRecordsMetrics() throws Exception {
        UUID decisionId = UUID.randomUUID();
        DecisionRecord record = new DecisionRecord(
                decisionId,
                "evt-1",
                "1.0.0",
                "ACCEPT",
                "ACCEPTED_BY_DEFAULT",
                "All policies passed",
                objectMapper.writeValueAsString(new InputReceivedV1("input-1", "email", "subj", "body", "from@test", 1700000000000L)),
                objectMapper.writeValueAsString(Map.of(
                        "decisionId", decisionId.toString(),
                        "strategy", "ai-override",
                        "aiAdvicePresent", true,
                        "aiAdviceStatus", "ACCEPTED",
                        "aiOverrideApplied", true,
                        "aiSuggestedClassification", "DEVICE_ERROR"
                )),
                Instant.parse("2026-03-18T10:15:30Z")
        );
        when(repository.findById(decisionId)).thenReturn(Optional.of(record));

        ReplayResult result = replayService.replayByDecisionId(decisionId);

        assertThat(result.newAttributes())
                .containsEntry("aiAdvicePresent", true)
                .containsEntry("aiAdviceStatus", "ACCEPTED")
                .containsEntry("aiOverrideApplied", true)
                .containsEntry("aiSuggestedClassification", "DEVICE_ERROR")
                .containsEntry("strategy", "rules-v1");
        assertThat(meterRegistry.get("triagemate.decision.total").tag("outcome", "accept").counter().count())
                .isEqualTo(1.0);
    }
}
