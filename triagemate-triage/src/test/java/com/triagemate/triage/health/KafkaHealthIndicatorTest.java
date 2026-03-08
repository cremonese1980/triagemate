package com.triagemate.triage.health;

import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaHealthIndicatorTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        indicator = new KafkaHealthIndicator(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void health_shouldReturnUp_whenKafkaReachable() {
        ProducerFactory<String, String> factory = mock(ProducerFactory.class);
        Producer<String, String> producer = mock(Producer.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn(factory);
        when(factory.createProducer()).thenReturn(producer);
        when(producer.partitionsFor(anyString())).thenReturn(Collections.<PartitionInfo>emptyList());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "Connected");
    }

    @SuppressWarnings("unchecked")
    @Test
    void health_shouldReturnDown_whenKafkaUnreachable() {
        ProducerFactory<String, String> factory = mock(ProducerFactory.class);
        Producer<String, String> producer = mock(Producer.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn(factory);
        when(factory.createProducer()).thenReturn(producer);
        when(producer.partitionsFor(anyString())).thenThrow(new RuntimeException("Connection refused"));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
        assertThat(health.getDetails()).containsEntry("error", "Connection refused");
    }

    @SuppressWarnings("unchecked")
    @Test
    void recordSuccessfulPublish_shouldUpdateLastPublishTimestamp() {
        ProducerFactory<String, String> factory = mock(ProducerFactory.class);
        Producer<String, String> producer = mock(Producer.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn(factory);
        when(factory.createProducer()).thenReturn(producer);
        when(producer.partitionsFor(anyString())).thenReturn(Collections.<PartitionInfo>emptyList());

        indicator.recordSuccessfulPublish();
        var health = indicator.health();

        assertThat(health.getDetails().get("lastSuccessfulPublish")).isNotEqualTo("none");
    }
}
