package com.triagemate.testsupport;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Import(KafkaTopicsTestConfig.class)
public abstract class KafkaIntegrationTestBase {

    @Container
    protected static KafkaContainer kafka =
            new KafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
                            .asCompatibleSubstituteFor("confluentinc/cp-kafka")
            ).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @Container
    protected static org.testcontainers.containers.PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("triagemate_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    protected static void properties(DynamicPropertyRegistry r) {

        // Kafka
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("triagemate.kafka.topics.input-received-v1",
                () -> "triagemate.ingest.input-received.v1");

        // Postgres
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        // Important: disable H2 auto-config surprises
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
    }
}