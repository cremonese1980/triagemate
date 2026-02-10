package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.EventEnvelope;
import com.triagemate.contracts.events.v1.InputReceivedV1;
import com.triagemate.triage.decision.DecisionContext;
import com.triagemate.triage.decision.DecisionContextFactory;
import com.triagemate.triage.decision.DecisionResult;
import com.triagemate.triage.decision.DecisionService;
import com.triagemate.triage.routing.DecisionRouter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;



@Component
public class InputReceivedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InputReceivedConsumer.class);
    private final DecisionContextFactory decisionContextFactory;
    private final DecisionService decisionService;
    private final DecisionRouter decisionRouter;

    public InputReceivedConsumer(
            DecisionContextFactory decisionContextFactory,
            DecisionService decisionService,
            DecisionRouter decisionRouter
    ) {
        this.decisionContextFactory = decisionContextFactory;
        this.decisionService = decisionService;
        this.decisionRouter = decisionRouter;
    }

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

        DecisionContext<InputReceivedV1> context = decisionContextFactory.fromEnvelope(envelope);
        DecisionResult result = decisionService.decide(context);

        decisionRouter.route(result, context);

        // Phase 7.3: route outcome and acknowledge
        ack.acknowledge();
    }

}

