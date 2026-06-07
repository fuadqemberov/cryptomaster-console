package com.cryptomaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppConfig {
    @Value("${cryptomaster.top-coin-count:200}")
    private int topCoinCount;

    @Value("${cryptomaster.intervals:1h,4h,1d}")
    private List<String> intervals;

    @Value("${binance.ws.base-url}")
    private String binanceWsBaseUrl;

    @Value("${binance.rest.base-url}")
    private String binanceRestBaseUrl;

    @Value("${binance.ohlc-intervals:1h,4h,1d}")
    private List<String> ohlcIntervals;

    @Value("${binance.ohlc-limit:300}")
    private int ohlcLimit;

    @Value("${cryptomaster.analysis-interval-seconds:300}")
    private int analysisIntervalSeconds;

    // ---- Futures / türev ayarları ----
    @Value("${binance.futures.rest.base-url:https://fapi.binance.com}")
    private String binanceFuturesRestBaseUrl;

    @Value("${binance.futures.ws.base-url:wss://fstream.binance.com/ws}")
    private String binanceFuturesWsBaseUrl;

    @Value("${cryptomaster.derivatives-enabled:true}")
    private boolean derivativesEnabled;

    // Türev verisi çok çağrı gerektirir; sadece en yüksek hacimli ilk N sembol için çekilir
    @Value("${cryptomaster.derivatives-top-count:80}")
    private int derivativesTopCount;

    // ---- Scalp (5-10 dk hızlı al-sat) ayarları ----
    @Value("${cryptomaster.scalp-enabled:true}")
    private boolean scalpEnabled;

    @Value("${cryptomaster.scalp-intervals:1m,5m,15m}")
    private List<String> scalpIntervals;

    // Slipajı önlemek için sadece en likit ilk N sembol scalp için taranır
    @Value("${cryptomaster.scalp-top-count:40}")
    private int scalpTopCount;

    @Value("${cryptomaster.scalp-interval-seconds:45}")
    private int scalpIntervalSeconds;

    @Value("${cryptomaster.scalp-ohlc-limit:240}")
    private int scalpOhlcLimit;

    // Getter'lar
    public int getTopCoinCount() { return topCoinCount; }
    public List<String> getIntervals() { return intervals; }
    public String getBinanceWsBaseUrl() { return binanceWsBaseUrl; }
    public String getBinanceRestBaseUrl() { return binanceRestBaseUrl; }
    public List<String> getOhlcIntervals() { return ohlcIntervals; }
    public int getOhlcLimit() { return ohlcLimit; }
    public int getAnalysisIntervalSeconds() { return analysisIntervalSeconds; }
    public String getBinanceFuturesRestBaseUrl() { return binanceFuturesRestBaseUrl; }
    public String getBinanceFuturesWsBaseUrl() { return binanceFuturesWsBaseUrl; }
    public boolean isDerivativesEnabled() { return derivativesEnabled; }
    public int getDerivativesTopCount() { return derivativesTopCount; }
    public boolean isScalpEnabled() { return scalpEnabled; }
    public List<String> getScalpIntervals() { return scalpIntervals; }
    public int getScalpTopCount() { return scalpTopCount; }
    public int getScalpIntervalSeconds() { return scalpIntervalSeconds; }
    public int getScalpOhlcLimit() { return scalpOhlcLimit; }
}