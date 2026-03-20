package com.triagemate.triage.control.decision;

import com.triagemate.triage.control.policy.Policy;
import com.triagemate.triage.control.policy.PolicyVersionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DecisionConfig {

    @Bean
    @Qualifier("deterministicDecisionService")
    DecisionService decisionService(List<Policy> policies, CostGuard costGuard,
                                    PolicyVersionProvider policyVersionProvider) {
        return new DefaultDecisionService(policies, costGuard, policyVersionProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    CostGuard costGuard() {
        return new AlwaysAllowCostGuard();
    }
}
