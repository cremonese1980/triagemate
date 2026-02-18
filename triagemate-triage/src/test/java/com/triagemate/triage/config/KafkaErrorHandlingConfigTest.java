package com.triagemate.triage.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaErrorHandlingConfigTest {

    @Test
    void buildsErrorHandlerWithConfiguredBackoff() {

        TriagemateKafkaProperties.Retry retry =
                new TriagemateKafkaProperties.Retry(500L, 3);

        TriagemateKafkaProperties.Consumer consumer =
                new TriagemateKafkaProperties.Consumer("group", retry);

        TriagemateKafkaProperties props =
                new TriagemateKafkaProperties(null, consumer);

        KafkaErrorHandlingConfig config =
                new KafkaErrorHandlingConfig(props);

        DefaultErrorHandler handler = config.kafkaErrorHandler();

        assertThat(handler).isNotNull();
    }
}
