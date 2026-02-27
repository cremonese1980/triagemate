package com.triagemate.triage.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triagemate.outbox")
public class OutboxProperties {

    private int batchSize = 20;
    private int maxAttempts = 3;
    private long baseBackoffMillis = 1000;
    private long maxBackoffMillis = 300_000; // 5 min
    private long lockDurationSeconds = 30;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseBackoffMillis() {
        return baseBackoffMillis;
    }

    public void setBaseBackoffMillis(long baseBackoffMillis) {
        this.baseBackoffMillis = baseBackoffMillis;
    }

    public long getMaxBackoffMillis() {
        return maxBackoffMillis;
    }

    public void setMaxBackoffMillis(long maxBackoffMillis) {
        this.maxBackoffMillis = maxBackoffMillis;
    }

    public long getLockDurationSeconds() {
        return lockDurationSeconds;
    }

    public void setLockDurationSeconds(long lockDurationSeconds) {
        this.lockDurationSeconds = lockDurationSeconds;
    }
}