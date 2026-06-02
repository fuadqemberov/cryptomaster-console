package com.cryptomaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppConfig {
    @Value("${cryptomaster.top-coin-count:100}")
    private int topCoinCount;

    @Value("${cryptomaster.batch-size:20}")
    private int batchSize;

    @Value("${cryptomaster.batch-wait-seconds:35}")
    private int batchWaitSeconds;

    @Value("${cryptomaster.coins:}")
    private List<String> coins;

    @Value("${cryptomaster.intervals:1h,4h,1d}")
    private List<String> intervals;

    public int getTopCoinCount() { return topCoinCount; }
    public int getBatchSize() { return batchSize; }
    public int getBatchWaitSeconds() { return batchWaitSeconds; }
    public List<String> getCoins() { return coins; }
    public List<String> getIntervals() { return intervals; }
}