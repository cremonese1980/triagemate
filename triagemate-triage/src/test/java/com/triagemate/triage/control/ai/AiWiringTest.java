package com.triagemate.triage.control.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6 Spring wiring verification: ensures AI beans are conditional on
 * {@code triagemate.ai.enabled}.
 */
class AiWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AiAdvisoryConfig.class,
                    AiResilienceConfig.class,
                    AiExecutorConfig.class
            ))
            .withUserConfiguration(InfraStubs.class);

    @Test
    void aiDisabled_noBeanCreated() {
        contextRunner
                .withPropertyValues("triagemate.ai.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(AiAdvisedDecisionService.class);
                    assertThat(ctx).doesNotHaveBean(AiDecisionAdvisor.class);
                    assertThat(ctx).doesNotHaveBean("aiCircuitBreaker");
                    assertThat(ctx).doesNotHaveBean("aiRetry");
                    assertThat(ctx).doesNotHaveBean("aiExecutor");
                });
    }

    @Test
    void aiPropertyMissing_noBeanCreated() {
        contextRunner
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(AiAdvisedDecisionService.class);
                    assertThat(ctx).doesNotHaveBean(AiDecisionAdvisor.class);
                });
    }

    @Configuration
    static class InfraStubs {
        @Bean
        public io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
