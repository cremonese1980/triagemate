package com.triagemate.testsupport;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class KafkaTopicsTestConfig {

    @Bean
    public NewTopic inputReceivedTopic() {
        return new NewTopic("triagemate.ingest.input-received.v1", 1, (short) 1);
    }

    @Bean
    public NewTopic decisionMadeTopic() {
        return new NewTopic("triagemate.triage.decision-made.v1", 1, (short) 1);
    }
}
