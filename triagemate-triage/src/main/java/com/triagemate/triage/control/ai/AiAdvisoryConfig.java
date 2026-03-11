package com.triagemate.triage.control.ai;

import com.triagemate.triage.control.decision.DecisionService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executor;

@Configuration
@ConditionalOnProperty(name = "triagemate.ai.enabled", havingValue = "true")
public class AiAdvisoryConfig {

    @Bean
    public ChatClient aiChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    public AiDecisionAdvisor aiDecisionAdvisor(
            ChatClient aiChatClient,
            PromptTemplateService promptTemplateService,
            AiResponseParser responseParser,
            PromptSanitizer promptSanitizer,
            AiAdvisoryProperties properties
    ) {
        return new SpringAiDecisionAdvisor(
                aiChatClient, promptTemplateService, responseParser,
                promptSanitizer, properties
        );
    }

    @Bean
    @Primary
    public DecisionService aiAdvisedDecisionService(
            DecisionService decisionService,
            AiDecisionAdvisor aiDecisionAdvisor,
            AiAdviceValidator adviceValidator,
            AiAuditService auditService,
            AiCostTracker costTracker,
            AiMetrics metrics,
            CircuitBreaker aiCircuitBreaker,
            Retry aiRetry,
            @Qualifier("aiExecutor") Executor aiExecutor,
            AiAdvisoryProperties properties
    ) {
        metrics.registerCircuitBreakerStateGauge(aiCircuitBreaker, properties.provider());

        return new AiAdvisedDecisionService(
                decisionService, aiDecisionAdvisor, adviceValidator,
                auditService, costTracker, metrics,
                aiCircuitBreaker, aiRetry, aiExecutor, properties
        );
    }
}
