package com.triagemate.ingest;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

@SpringBootTest
class TriagemateIngestApplicationTests {

	@Test
	void contextLoads() {}

	@Configuration
	static class TestKafkaConfig {

		@Bean
		KafkaTemplate<String, Object> kafkaTemplate() {
			var props = Map.<String, Object>of(
					ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092",
					ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
					ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
			);

			return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
		}
	}
}
