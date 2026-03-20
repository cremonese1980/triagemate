package com.triagemate.triage.replay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayControllerTest {

    @Mock
    private ReplayService replayService;

    private ReplayController controller;

    @BeforeEach
    void setUp() {
        controller = new ReplayController(replayService);
    }

    @Test
    void replayDecision_delegatesToService() {
        UUID decisionId = UUID.randomUUID();
        ReplayResult expected = ReplayResult.compare(
                decisionId, "ACCEPT", "1.0.0", Map.of(),
                "ACCEPT", "2.0.0", Map.of()
        );
        when(replayService.replayByDecisionId(decisionId)).thenReturn(expected);

        ReplayResult result = controller.replayDecision(decisionId);

        assertThat(result).isSameAs(expected);
        verify(replayService).replayByDecisionId(decisionId);
    }

    @Test
    void replayBatch_delegatesToServiceForEachId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ReplayResult r1 = ReplayResult.compare(id1, "ACCEPT", "1.0.0", Map.of(), "ACCEPT", "1.0.0", Map.of());
        ReplayResult r2 = ReplayResult.compare(id2, "ACCEPT", "1.0.0", Map.of(), "REJECT", "2.0.0", Map.of());
        when(replayService.replayByDecisionId(id1)).thenReturn(r1);
        when(replayService.replayByDecisionId(id2)).thenReturn(r2);

        var request = new ReplayController.BatchReplayRequest(List.of(id1, id2));
        List<ReplayResult> results = controller.replayBatch(request);

        assertThat(results).containsExactly(r1, r2);
    }

    @Test
    void replayDecisionByEventId_delegatesToService() {
        String eventId = "evt-001";
        ReplayResult expected = ReplayResult.compare(
                UUID.randomUUID(), "ACCEPT", "1.0.0", Map.of(),
                "ACCEPT", "1.0.0", Map.of()
        );
        when(replayService.replayByEventId(eventId)).thenReturn(expected);

        ReplayResult result = controller.replayDecisionByEventId(eventId);

        assertThat(result).isSameAs(expected);
        verify(replayService).replayByEventId(eventId);
    }

    @Test
    void handleNotFound_returns404Message() {
        UUID decisionId = UUID.randomUUID();
        var ex = new DecisionNotFoundException(decisionId);

        Map<String, String> response = controller.handleNotFound(ex);

        assertThat(response).containsKey("error");
        assertThat(response.get("error")).contains(decisionId.toString());
    }

    @Test
    void replayDecisionByEventId_whenNotFound_delegatesException() {
        String eventId = "evt-missing-999";

        Map<String, String> response = controller.handleNotFound(
                new DecisionNotFoundException(eventId));

        assertThat(response).containsKey("error");
        assertThat(response.get("error")).contains(eventId);
    }

    @Test
    void handleNotFound_returns404Message_forEventId() {
        String eventId = "evt-not-found-001";
        var ex = new DecisionNotFoundException(eventId);

        Map<String, String> response = controller.handleNotFound(ex);

        assertThat(response).containsKey("error");
        assertThat(response.get("error")).contains(eventId);
        assertThat(response.get("error")).contains("event");
    }

    @Test
    void batchReplayRequest_handlesNullList() {
        var request = new ReplayController.BatchReplayRequest(null);

        assertThat(request.decisionIds()).isEmpty();
    }

    @Test
    void replayDecisionByEventId_whenNotFound_returns404() {
        String eventId = "evt-missing";
        var ex = new DecisionNotFoundException(eventId);
        when(replayService.replayByEventId(eventId)).thenThrow(ex);

        DecisionNotFoundException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                DecisionNotFoundException.class,
                () -> controller.replayDecisionByEventId(eventId)
        );

        Map<String, String> response = controller.handleNotFound(thrown);

        assertThat(response).containsEntry("error", ex.getMessage());
        verify(replayService).replayByEventId(eventId);
    }

}
