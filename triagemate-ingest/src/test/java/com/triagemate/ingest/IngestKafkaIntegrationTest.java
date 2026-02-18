package com.triagemate.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IngestKafkaIntegrationTest extends KafkaIntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    @Test
    void post_ingest_publishes_event_to_kafka() throws Exception {

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps(
                        kafka.getBootstrapServers(),
                        "test-group",
                        "false");

        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProps);
        var consumer = consumerFactory.createConsumer();
        consumer.subscribe(java.util.List.of("triagemate.ingest.input-received.v1"));

        var headers = new HttpHeaders();
        headers.add("X-Request-Id", "req-test-1");

        var body = Map.of("channel", "email", "content", "hello kafka");

        var response = rest.postForEntity(
                "/api/ingest/messages",
                new org.springframework.http.HttpEntity<>(body, headers),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);

        var record = records.iterator().next();
        var envelope = objectMapper.readValue(record.value(), EventEnvelope.class);

        assertThat(envelope.eventType()).isEqualTo("triagemate.ingest.input-received");
        assertThat(envelope.eventVersion()).isEqualTo(1);

        var payload = objectMapper.convertValue(envelope.payload(), InputReceivedV1.class);
        assertThat(payload.text()).isEqualTo("hello kafka");
        assertThat(payload.channel()).isEqualTo("email");
    }
}
