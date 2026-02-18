package com.triagemate.testsupport;


import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
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
            );

    @DynamicPropertySource
    protected static void kafkaProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("triagemate.kafka.topics.input-received-v1",
                () -> "triagemate.ingest.input-received.v1");
    }
}
