package com.cryptomaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppConfig {
    @Value("${cryptomaster.top-coin-count:50}")  // varsayılan 50
    private int topCoinCount;

    @Value("${cryptomaster.coins:}") // boş bırakılabilir
    private List<String> coins;

    @Value("${cryptomaster.intervals:1h,4h,1d}")
    private List<String> intervals;

    public int getTopCoinCount() { return topCoinCount; }
    public List<String> getCoins() { return coins; }
    public List<String> getIntervals() { return intervals; }
}