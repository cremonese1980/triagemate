package com.triagemate.triage.control.policy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PolicyConfig {

    @Bean
    @ConditionalOnMissingBean
    List<Policy> policies() {
        return List.of(new AcceptAllPolicy());
    }
}
