package com.triagemate.triage.kafka;

import java.util.concurrent.CountDownLatch;

public final class TestLatches {
    static final CountDownLatch INPUT_RECEIVED = new CountDownLatch(1);

    private TestLatches() {}
}
