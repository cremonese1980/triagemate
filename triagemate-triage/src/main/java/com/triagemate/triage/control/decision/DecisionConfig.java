package com.triagemate.triage.control.decision;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecisionConfig {

    @Bean
    DecisionService decisionService() {
        return new DefaultDecisionService();
    }
}
