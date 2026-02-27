package com.triagemate.triage.config;

import com.triagemate.triage.exception.InvalidEventException;
import com.triagemate.triage.exception.RetryableDecisionException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    private final TriagemateKafkaProperties triagemateKafkaProperties;

    public KafkaErrorHandlingConfig(TriagemateKafkaProperties triagemateKafkaProperties) {
        this.triagemateKafkaProperties = triagemateKafkaProperties;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, Object> kafkaTemplate) {

        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(record.topic() + ".dlt", record.partition()));

        var backOff = new FixedBackOff(
                triagemateKafkaProperties.consumer().retry().backoffMs(),
                triagemateKafkaProperties.consumer().retry().maxRetries()
        );

        log.debug("Retry config: backoff={}ms, maxRetries={}",
                triagemateKafkaProperties.consumer().retry().backoffMs(),
                triagemateKafkaProperties.consumer().retry().maxRetries());

        var handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addRetryableExceptions(RetryableDecisionException.class);
        handler.addNotRetryableExceptions(InvalidEventException.class);

        handler.setRetryListeners((ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) -> {
            log.warn(
                    "kafka.retry attempt={} topic={} partition={} offset={} exceptionType={} message={}",
                    deliveryAttempt,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getClass().getSimpleName(),
                    safeMsg(ex)
            );
        });

        return handler;
    }

    private static String safeMsg(Exception ex) {
        var m = ex.getMessage();
        return m == null ? "" : m;
    }
}
