package com.triagemate.triage.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

    @Bean
    DecisionRouter decisionRouter(DecisionOutcomePublisher decisionOutcomePublisher) {
        return new DefaultDecisionRouter(decisionOutcomePublisher);
    }
}
