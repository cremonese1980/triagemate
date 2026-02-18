package com.triagemate.triage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TriagemateKafkaPropertiesBindingTest {

    @Test
    void nestedStructure_bindsCorrectly() {

        MockEnvironment env = new MockEnvironment()
                .withProperty("triagemate.kafka.topics.input-received", "input-topic")
                .withProperty("triagemate.kafka.topics.decision-made", "decision-topic")
                .withProperty("triagemate.kafka.consumer.group-id", "group-1")
                .withProperty("triagemate.kafka.consumer.retry.backoff-ms", "1000")
                .withProperty("triagemate.kafka.consumer.retry.max-retries", "2");

        Binder binder = Binder.get(env);

        TriagemateKafkaProperties props =
                binder.bind(
                        "triagemate.kafka",
                        Bindable.of(TriagemateKafkaProperties.class)
                ).orElseThrow(()-> new IllegalStateException("Kafka properties not bound"));

        assertThat(props.topics().inputReceived()).isEqualTo("input-topic");
        assertThat(props.topics().decisionMade()).isEqualTo("decision-topic");

        assertThat(props.consumer().groupId()).isEqualTo("group-1");
        assertThat(props.consumer().retry().backoffMs()).isEqualTo(1000L);
        assertThat(props.consumer().retry().maxRetries()).isEqualTo(2);
    }
}
