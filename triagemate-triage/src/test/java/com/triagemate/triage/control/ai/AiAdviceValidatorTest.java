package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionOutcome;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.decision.ReasonCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAdviceValidatorTest {

    private AiAdviceValidator validator;
    private DecisionResult deterministicResult;

    @BeforeEach
    void setUp() {
        AiAdvisoryProperties props = new AiAdvisoryProperties(
                true, "test",
                Set.of("DEVICE_ERROR", "NETWORK_ISSUE", "NORMAL"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.70, 0.85)
        );
        validator = new AiAdviceValidator(props);
        deterministicResult = deterministicResult();
    }

    @Test
    void noAdvice_whenAdviceIsNone() {
        ValidatedAdvice result = validator.validate(deterministicResult, AiDecisionAdvice.NONE);
        assertEquals(ValidatedAdvice.Status.NO_ADVICE, result.status());
    }

    @Test
    void rejected_whenConfidenceTooLow() {
        AiDecisionAdvice advice = createAdvice("DEVICE_ERROR", 0.50, false);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.REJECTED, result.status());
        assertTrue(result.rejectionReason().contains("Confidence too low"));
    }

    @Test
    void rejected_whenClassificationInvalid() {
        AiDecisionAdvice advice = createAdvice("INVALID_CLASS", 0.90, true);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.REJECTED, result.status());
        assertTrue(result.rejectionReason().contains("Invalid classification"));
    }

    @Test
    void rejected_whenClassificationIsNull() {
        AiDecisionAdvice advice = createAdvice(null, 0.90, true);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.REJECTED, result.status());
        assertTrue(result.rejectionReason().contains("Invalid classification"));
    }

    @Test
    void accepted_whenHighConfidenceAndOverride() {
        AiDecisionAdvice advice = createAdvice("DEVICE_ERROR", 0.90, true);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.ACCEPTED, result.status());
        assertTrue(result.isAccepted());
    }

    @Test
    void advisory_whenHighConfidenceButNoOverride() {
        AiDecisionAdvice advice = createAdvice("DEVICE_ERROR", 0.90, false);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.ADVISORY, result.status());
    }

    @Test
    void advisory_whenExactlyAtSuggestionThreshold() {
        AiDecisionAdvice advice = createAdvice("DEVICE_ERROR", 0.70, false);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.ADVISORY, result.status());
    }

    @Test
    void accepted_whenExactlyAtOverrideThreshold() {
        AiDecisionAdvice advice = createAdvice("DEVICE_ERROR", 0.85, true);
        ValidatedAdvice result = validator.validate(deterministicResult, advice);
        assertEquals(ValidatedAdvice.Status.ACCEPTED, result.status());
    }

    @Test
    void thresholds_areConfigurable() {
        AiAdvisoryProperties customThresholds = new AiAdvisoryProperties(
                true,
                "test",
                Set.of("DEVICE_ERROR"),
                new AiAdvisoryProperties.Timeouts(Duration.ofSeconds(5)),
                new AiAdvisoryProperties.Cost(0.05, 100.0),
                new AiAdvisoryProperties.Validation(0.60, 0.80)
        );
        AiAdviceValidator customValidator = new AiAdviceValidator(customThresholds);

        ValidatedAdvice result = customValidator.validate(deterministicResult, createAdvice("DEVICE_ERROR", 0.80, true));

        assertEquals(ValidatedAdvice.Status.ACCEPTED, result.status());
    }

    private DecisionResult deterministicResult() {
        return DecisionResult.of(
                DecisionOutcome.ACCEPT,
                "deterministic",
                Map.of("strategy", "rules"),
                ReasonCode.ACCEPTED_BY_DEFAULT,
                "All policies passed"
        );
    }

    private AiDecisionAdvice createAdvice(String classification, double confidence, boolean override) {
        return new AiDecisionAdvice(
                classification, confidence, "test reasoning", override,
                "test", "model", "v1", "1.0.0", "hash",
                10, 20, 0.001, 100
        );
    }
}
