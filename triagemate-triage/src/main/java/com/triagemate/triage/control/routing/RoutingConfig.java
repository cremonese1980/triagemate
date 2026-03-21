package com.triagemate.triage.control.routing;

import com.triagemate.triage.control.rag.ExplanationCurationService;
import com.triagemate.triage.persistence.DecisionPersistenceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
public class RoutingConfig {

    @Bean
    DecisionRouter decisionRouter(DecisionOutcomePublisher decisionOutcomePublisher,
                                  DecisionPersistenceService decisionPersistenceService,
                                  @Nullable ExplanationCurationService curationService) {
        return new DefaultDecisionRouter(decisionOutcomePublisher, decisionPersistenceService, curationService);
    }

    @Bean
    @ConditionalOnMissingBean
    DecisionReplayService decisionReplayService() {
        return new NoOpDecisionReplayService();
    }
}
