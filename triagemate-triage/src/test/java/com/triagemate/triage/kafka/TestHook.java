package com.triagemate.triage.kafka;

import com.triagemate.contracts.events.v1.InputReceivedV1;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TestHook {

    @EventListener
    void onInputReceived(InputReceivedV1 ignored) {
        TestLatches.INPUT_RECEIVED.countDown();
    }
}
