package com.triagemate.triage.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("dev")
public class KafkaTopicsDevConfig {

    @Bean
    public NewTopic inputReceivedTopic() {
        return TopicBuilder
                .name("triagemate.ingest.input-received.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inputReceivedDltTopic() {
        return TopicBuilder
                .name("triagemate.ingest.input-received.v1.dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic decisionMadeTopic() {
        return TopicBuilder
                .name("triagemate.triage.decision-made.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic decisionMadeDltTopic() {
        return TopicBuilder
                .name("triagemate.triage.decision-made.v1.dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
