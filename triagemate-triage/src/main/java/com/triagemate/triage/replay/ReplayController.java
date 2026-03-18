package com.triagemate.triage.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/replay")
@Profile("dev")
public class ReplayController {

    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping("/{decisionId}")
    public ReplayResult replayDecision(@PathVariable UUID decisionId) {
        log.info("Replay request for decisionId={}", decisionId);
        return replayService.replayByDecisionId(decisionId);
    }

    @PostMapping("/batch")
    public List<ReplayResult> replayBatch(@RequestBody BatchReplayRequest request) {
        log.info("Batch replay request for {} decision(s)", request.decisionIds().size());
        return request.decisionIds().stream()
                .map(replayService::replayByDecisionId)
                .toList();
    }

    @ExceptionHandler(DecisionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(DecisionNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    public record BatchReplayRequest(List<UUID> decisionIds) {
        public BatchReplayRequest {
            decisionIds = decisionIds == null ? List.of() : List.copyOf(decisionIds);
        }
    }
}
