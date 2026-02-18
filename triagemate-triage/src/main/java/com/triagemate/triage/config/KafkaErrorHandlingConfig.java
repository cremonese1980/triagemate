package com.triagemate.triage.config;

import com.triagemate.triage.exception.InvalidEventException;
import com.triagemate.triage.exception.RetrIableDecisionException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    private final TriagemateKafkaProperties triagemateKafkaProperties;

    @Autowired
    public KafkaErrorHandlingConfig(TriagemateKafkaProperties triagemateKafkaProperties) {
        this.triagemateKafkaProperties = triagemateKafkaProperties;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {

        var backOff = new FixedBackOff(
                triagemateKafkaProperties.consumer().retry().backoffMs(),
                triagemateKafkaProperties.consumer().retry().maxRetries()
        );

        log.debug("Retry config → backoff={}, maxRetries={}",
                triagemateKafkaProperties.consumer().retry().backoffMs(),
                triagemateKafkaProperties.consumer().retry().maxRetries());

        var handler = new DefaultErrorHandler(backOff);

        handler.addRetryableExceptions(RetrIableDecisionException.class);
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
