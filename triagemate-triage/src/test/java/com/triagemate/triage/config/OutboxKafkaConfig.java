package com.triagemate.triage.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OutboxKafkaConfig {

    @Bean
    @Qualifier("outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            KafkaProperties properties
    ) {

        Map<String, Object> props =
                new HashMap<>(properties.buildProducerProperties(null));

        props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        DefaultKafkaProducerFactory<String, String> factory =
                new DefaultKafkaProducerFactory<>(props);

        return new KafkaTemplate<>(factory);
    }
}