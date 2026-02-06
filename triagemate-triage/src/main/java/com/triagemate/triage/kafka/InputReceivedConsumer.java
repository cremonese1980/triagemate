package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;



@Component
public class InputReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InputReceivedConsumer.class);



    @KafkaListener(
            topics = "${triagemate.kafka.topics.input-received}",
            groupId = "${triagemate.kafka.consumer.group-id}"
    )
    public void onMessage(
            ConsumerRecord<String, EventEnvelope<InputReceivedV1>> record,
            Acknowledgment ack
    ) {
        EventEnvelope<InputReceivedV1> envelope = record.value();

        if (envelope == null) {
            log.warn("Received null envelope. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            ack.acknowledge();
            return;
        }

        log.info(
                "Consumed event: eventId={}, type={}, version={}, key={}, producer={}, requestId={}, correlationId={}, topic={}, partition={}, offset={}",
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                record.key(),
                envelope.producer() != null ? envelope.producer().service() : null,
                envelope.trace() != null ? envelope.trace().requestId() : null,
                envelope.trace() != null ? envelope.trace().correlationId() : null,
                record.topic(),
                record.partition(),
                record.offset()
        );

        // Phase 7.1: no business logic, just acknowledge
        ack.acknowledge();


    }

}


