package com.cryptomaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "cryptomaster")
public class AppConfig {
    private List<String> coins;
    private List<String> intervals;

    public List<String> getCoins() { return coins; }
    public void setCoins(List<String> coins) { this.coins = coins; }

    public List<String> getIntervals() { return intervals; }
    public void setIntervals(List<String> intervals) { this.intervals = intervals; }
}