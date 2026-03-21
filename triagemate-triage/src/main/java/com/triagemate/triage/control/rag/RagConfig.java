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
    public ContentHasher contentHasher() {
        return new ContentHasher();
    }

    @Bean
    public JdbcDecisionExplanationRepository decisionExplanationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDecisionExplanationRepository(jdbcTemplate);
    }

    @Bean
    public ExplanationCurationService explanationCurationService(
            DecisionExplanationRepository repository,
            PolicyFamilyProvider policyFamilyProvider,
            ContentHasher contentHasher,
            RagProperties properties
    ) {
        return new ExplanationCurationService(
                repository, policyFamilyProvider, contentHasher, properties.defaultQualityScore()
        );
    }

    @Bean
    public EmbeddingTextPreparer embeddingTextPreparer() {
        return new EmbeddingTextPreparer();
    }

    @Bean
    public JdbcEmbeddingCacheRepository embeddingCacheRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcEmbeddingCacheRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public SpringAiEmbeddingService springAiEmbeddingService(
            EmbeddingModel embeddingModel,
            RagProperties properties
    ) {
        return new SpringAiEmbeddingService(embeddingModel, properties.embeddingModel());
    }

    @Bean
    public JdbcDecisionEmbeddingRepository decisionEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDecisionEmbeddingRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(SpringAiEmbeddingService.class)
    public CachedEmbeddingService embeddingService(
            SpringAiEmbeddingService springAiEmbeddingService,
            EmbeddingCacheRepository cacheRepository,
            ContentHasher contentHasher
    ) {
        return new CachedEmbeddingService(springAiEmbeddingService, cacheRepository, contentHasher);
    }
}
