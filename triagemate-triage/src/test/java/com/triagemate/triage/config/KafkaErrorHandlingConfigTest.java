package com.triagemate.triage.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaErrorHandlingConfigTest {

    @SuppressWarnings("unchecked")
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

        KafkaOperations<String, Object> kafkaTemplate = mock(KafkaOperations.class);

        DefaultErrorHandler handler = config.kafkaErrorHandler(kafkaTemplate);

        assertThat(handler).isNotNull();
    }
}
