package com.triagemate.triage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPropertiesBindingTest {

    @Test
    void retryProperties_bindsFromYamlCorrectly() {

        MockEnvironment env = new MockEnvironment()
                .withProperty("triagemate.kafka.consumer.retry.backoff-ms", "500")
                .withProperty("triagemate.kafka.consumer.retry.max-retries", "3");

        Binder binder = Binder.get(env);

        TriagemateKafkaProperties.Retry retry =
                binder.bind(
                        "triagemate.kafka.consumer.retry",
                        Bindable.of(TriagemateKafkaProperties.Retry.class)
                ).orElseThrow(()->new IllegalStateException("Retry properties not bound"));

        assertThat(retry.backoffMs()).isEqualTo(500L);
        assertThat(retry.maxRetries()).isEqualTo(3);
    }
}
