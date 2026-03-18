package com.triagemate.triage.control.routing;

import com.triagemate.triage.persistence.DecisionPersistenceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

    @Bean
    DecisionRouter decisionRouter(DecisionOutcomePublisher decisionOutcomePublisher,
                                  DecisionPersistenceService decisionPersistenceService) {
        return new DefaultDecisionRouter(decisionOutcomePublisher, decisionPersistenceService);
    }

    @Bean
    @ConditionalOnMissingBean
    DecisionReplayService decisionReplayService() {
        return new NoOpDecisionReplayService();
    }
}
