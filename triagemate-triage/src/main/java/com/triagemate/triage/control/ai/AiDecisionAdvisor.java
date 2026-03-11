package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;

/**
 * AI advisory interface. Implementations provide classification suggestions
 * based on the deterministic decision and event context.
 * <p>
 * Contract: if AI fails for any reason, return {@link AiDecisionAdvice#NONE}.
 */
public interface AiDecisionAdvisor {

    AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult);
}
