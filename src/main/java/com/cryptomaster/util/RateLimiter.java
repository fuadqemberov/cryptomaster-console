package com.cryptomaster.util;

public class RateLimiter {
    private final long minIntervalMs;
    private long lastCallTime = 0;

    public RateLimiter(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long diff = now - lastCallTime;
        if (diff < minIntervalMs) {
            try {
                Thread.sleep(minIntervalMs - diff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallTime = System.currentTimeMillis();
    }
}