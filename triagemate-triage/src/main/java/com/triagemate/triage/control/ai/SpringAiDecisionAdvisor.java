package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.client.HttpStatusCodeException;

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

    public SpringAiDecisionAdvisor(
            ChatClient chatClient,
            PromptTemplateService promptTemplateService,
            AiResponseParser responseParser,
            PromptSanitizer promptSanitizer,
            AiAdvisoryProperties properties
    ) {
        this.chatClient = chatClient;
        this.promptTemplateService = promptTemplateService;
        this.responseParser = responseParser;
        this.promptSanitizer = promptSanitizer;
        this.properties = properties;
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

        return promptTemplateService.render(Map.of(
                "classification", deterministicResult.outcome().name(),
                "outcome", deterministicResult.outcome().name(),
                "reason", deterministicResult.reason() != null ? deterministicResult.reason() : "",
                "eventType", context.eventType() != null ? context.eventType() : "",
                "payloadSummary", payloadSummary,
                "classificationRule", classificationRule
        ));
    }
}
