package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionContext;
import com.triagemate.triage.control.decision.DecisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

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
            String prompt = buildPrompt(context, deterministicResult);

            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long latencyMs = System.currentTimeMillis() - start;

            Set<String> allowed = properties.allowedClassifications();
            AiClassificationResponse parsed = responseParser.parse(rawResponse, allowed);

            return new AiDecisionAdvice(
                    parsed.suggestedClassification(),
                    parsed.confidence(),
                    parsed.reasoning(),
                    parsed.recommendsOverride(),
                    properties.provider(),
                    null, // model extracted at config level
                    null, // modelVersion
                    promptTemplateService.getPromptVersion(),
                    promptTemplateService.getPromptHash(),
                    0, 0, // token counts — not always available from all providers
                    0.0,
                    latencyMs
            );
        } catch (AiAdvisoryException e) {
            throw e; // let caller handle
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("AI advisory call failed after {}ms: {}", latencyMs, e.getMessage());
            throw new TransientAiException("AI provider error: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(DecisionContext<?> context, DecisionResult deterministicResult) {
        String payloadSummary = promptSanitizer.sanitize(
                context.payload() != null ? context.payload().toString() : ""
        );

        Set<String> allowed = properties.allowedClassifications();
        String classificationsStr = allowed.isEmpty() ? "ANY" : String.join(", ", allowed);

        return promptTemplateService.render(Map.of(
                "classification", deterministicResult.outcome().name(),
                "outcome", deterministicResult.outcome().name(),
                "reason", deterministicResult.reason() != null ? deterministicResult.reason() : "",
                "eventType", context.eventType() != null ? context.eventType() : "",
                "payloadSummary", payloadSummary,
                "allowedClassifications", classificationsStr
        ));
    }
}
