package com.triagemate.testsupport;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class KafkaTopicsTestConfig {

    @Bean
    public NewTopic inputReceivedTopic(
            @Value("${triagemate.kafka.topics.input-received}") String name
    ) {
        return new NewTopic(name, 1, (short) 1);
    }

    @Bean
    public NewTopic decisionMadeTopic(
            @Value("${triagemate.kafka.topics.decision-made}") String name
    ) {
        return new NewTopic(name, 1, (short) 1);
    }

    @Bean
    public NewTopic inputReceivedDltTopic(
            @Value("${triagemate.kafka.topics.input-received}") String name
    ) {
        return new NewTopic(name + ".dlt", 1, (short) 1);
    }
}
