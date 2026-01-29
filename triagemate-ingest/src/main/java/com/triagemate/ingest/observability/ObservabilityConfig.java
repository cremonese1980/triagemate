package com.triagemate.ingest.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }
}
