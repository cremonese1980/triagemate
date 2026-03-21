package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import com.triagemate.triage.control.policy.PolicyFamilyProvider;
import com.triagemate.triage.control.rag.DecisionExplanationContext;
import com.triagemate.triage.control.rag.DecisionMemoryService;
import com.triagemate.triage.control.rag.RagProperties;
import com.triagemate.triage.control.rag.RetrievalFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring AI-backed implementation of {@link AiDecisionAdvisor}.
 * Provider-agnostic: works with any {@link ChatClient} (Anthropic, Ollama, OpenAI, etc).
 */
public class SpringAiDecisionAdvisor implements AiDecisionAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDecisionAdvisor.class);

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final AiResponseParser responseParser;
    private final PromptSanitizer promptSanitizer;
    private final AiAdvisoryProperties properties;
    private final DecisionMemoryService memoryService;
    private final PolicyFamilyProvider policyFamilyProvider;
    private final RagProperties ragProperties;
    private final HistoricalContextFormatter contextFormatter;

    public SpringAiDecisionAdvisor(
            ChatClient chatClient,
            PromptTemplateService promptTemplateService,
            AiResponseParser responseParser,
            PromptSanitizer promptSanitizer,
            AiAdvisoryProperties properties
    ) {
        this(chatClient, promptTemplateService, responseParser, promptSanitizer,
                properties, null, null, null);
    }

    public SpringAiDecisionAdvisor(
            ChatClient chatClient,
            PromptTemplateService promptTemplateService,
            AiResponseParser responseParser,
            PromptSanitizer promptSanitizer,
            AiAdvisoryProperties properties,
            DecisionMemoryService memoryService,
            PolicyFamilyProvider policyFamilyProvider,
            RagProperties ragProperties
    ) {
        this.chatClient = chatClient;
        this.promptTemplateService = promptTemplateService;
        this.responseParser = responseParser;
        this.promptSanitizer = promptSanitizer;
        this.properties = properties;
        this.memoryService = memoryService;
        this.policyFamilyProvider = policyFamilyProvider;
        this.ragProperties = ragProperties;
        this.contextFormatter = new HistoricalContextFormatter();
    }

    @Override
    public AiDecisionAdvice advise(DecisionContext<?> context, DecisionResult deterministicResult) {
        long start = System.currentTimeMillis();
        try {

            log.info("Deterministic result: {}", deterministicResult.toString());
            String prompt = buildPrompt(context, deterministicResult);

            ChatResponse chatResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String rawResponse = chatResponse.getResult().getOutput().getText();

            log.info("AI raw response: {}", rawResponse);

            long latencyMs = System.currentTimeMillis() - start;

            Set<String> allowed = properties.allowedClassifications();
            AiClassificationResponse parsed = responseParser.parse(rawResponse, allowed);

            log.info("AI parsed: classification={}, confidence={}", parsed.suggestedClassification(), parsed.confidence());

            int inputTokens = 0;
            int outputTokens = 0;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                inputTokens = (int) usage.getPromptTokens();
                outputTokens = (int) usage.getCompletionTokens();
            }
            double costUsd = estimateCost(inputTokens, outputTokens);

            return new AiDecisionAdvice(
                    parsed.suggestedClassification(),
                    parsed.confidence(),
                    parsed.reasoning(),
                    parsed.recommendsOverride(),
                    properties.provider(),
                    properties.model(),
                    properties.modelVersion(),
                    promptTemplateService.getPromptVersion(),
                    promptTemplateService.getPromptHash(),
                    inputTokens, outputTokens,
                    costUsd,
                    latencyMs
            );
        } catch (AiAdvisoryException e) {
            throw e; // let caller handle
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("AI advisory call failed after {}ms: {}", latencyMs, e.getMessage());
            throw classifyException(e);
        }
    }

    /* visible for testing */
    AiAdvisoryException classifyException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof HttpStatusCodeException httpEx) {
                int status = httpEx.getStatusCode().value();
                if (status == 429 || status >= 500) {
                    return new TransientAiException(
                            "AI provider error (HTTP " + status + "): " + httpEx.getMessage(), e);
                }
                return new PermanentAiException(
                        "AI provider error (HTTP " + status + "): " + httpEx.getMessage(), e);
            }
            cause = cause.getCause();
        }
        return new TransientAiException("AI provider error: " + e.getMessage(), e);
    }

    private double estimateCost(int inputTokens, int outputTokens) {
        // Conservative cost estimation per 1M tokens (Anthropic Claude Sonnet pricing)
        // Input: $3/1M tokens, Output: $15/1M tokens
        double inputCost = (inputTokens / 1_000_000.0) * 3.0;
        double outputCost = (outputTokens / 1_000_000.0) * 15.0;
        return inputCost + outputCost;
    }

    private String buildPrompt(DecisionContext<?> context, DecisionResult deterministicResult) {
        String payloadSummary = promptSanitizer.sanitize(
                context.payload() != null ? context.payload().toString() : ""
        );

        Set<String> allowed = properties.allowedClassifications();
        String classificationRule = allowed.isEmpty()
                ? "free-form domain label (not restricted)"
                : "one of: " + String.join(", ", allowed);

        String historicalContext = retrieveHistoricalContext(deterministicResult);

        Map<String, String> variables = new HashMap<>();
        variables.put("classification", deterministicResult.outcome().name());
        variables.put("outcome", deterministicResult.outcome().name());
        variables.put("reason", deterministicResult.reason() != null ? deterministicResult.reason() : "");
        variables.put("eventType", context.eventType() != null ? context.eventType() : "");
        variables.put("payloadSummary", payloadSummary);
        variables.put("classificationRule", classificationRule);
        variables.put("historicalContext", historicalContext);

        return promptTemplateService.render(variables);
    }

    private String retrieveHistoricalContext(DecisionResult deterministicResult) {
        if (memoryService == null) {
            return "";
        }

        try {
            RagProperties.Retrieval retrieval = ragProperties != null
                    ? ragProperties.retrieval()
                    : new RagProperties.Retrieval(3, 0.5, null);

            String policyFamily = policyFamilyProvider != null
                    ? policyFamilyProvider.currentFamily()
                    : null;

            String classification = deterministicResult.reason() != null
                    ? deterministicResult.reason()
                    : "";

            RetrievalFilters filters = new RetrievalFilters(
                    List.of(),
                    retrieval.minQualityScore(),
                    policyFamily,
                    retrieval.minPolicyVersion()
            );

            List<DecisionExplanationContext> similar =
                    memoryService.findSimilarDecisions(classification, retrieval.topK(), filters);

            return contextFormatter.format(similar);
        } catch (Exception e) {
            log.warn("Historical context retrieval failed, proceeding without context", e);
            return "";
        }
    }
}
