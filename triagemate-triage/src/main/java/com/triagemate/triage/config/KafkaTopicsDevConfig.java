package com.triagemate.triage.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("dev")
public class KafkaTopicsDevConfig {

    @Bean
    public NewTopic inputReceivedTopic(
            @Value("${triagemate.kafka.topics.input-received}") String name
    ) {
        return TopicBuilder
                .name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inputReceivedDltTopic(
            @Value("${triagemate.kafka.topics.input-received}") String name
    ) {
        return TopicBuilder
                .name(name + ".dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic decisionMadeTopic(
            @Value("${triagemate.kafka.topics.decision-made}") String name
    ) {
        return TopicBuilder
                .name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic decisionMadeDltTopic(
            @Value("${triagemate.kafka.topics.decision-made}") String name
    ) {
        return TopicBuilder
                .name(name + ".dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
