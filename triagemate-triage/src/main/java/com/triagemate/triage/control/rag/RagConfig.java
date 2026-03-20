package com.triagemate.triage.control.rag;

import com.triagemate.triage.control.policy.PolicyFamilyProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "triagemate.rag.enabled", havingValue = "true")
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public JdbcDecisionExplanationRepository decisionExplanationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDecisionExplanationRepository(jdbcTemplate);
    }

    @Bean
    public ExplanationCurationService explanationCurationService(
            DecisionExplanationRepository repository,
            PolicyFamilyProvider policyFamilyProvider,
            RagProperties properties
    ) {
        return new ExplanationCurationService(
                repository, policyFamilyProvider, properties.defaultQualityScore()
        );
    }

    @Bean
    public EmbeddingTextPreparer embeddingTextPreparer() {
        return new EmbeddingTextPreparer();
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public SpringAiEmbeddingService embeddingService(
            EmbeddingModel embeddingModel,
            RagProperties properties
    ) {
        return new SpringAiEmbeddingService(embeddingModel, properties.embeddingModel());
    }
}
