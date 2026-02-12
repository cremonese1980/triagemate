package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.Policy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DecisionConfig {

    @Bean
    DecisionService decisionService(List<Policy> policies) {
        return new DefaultDecisionService(policies);
    }
}
