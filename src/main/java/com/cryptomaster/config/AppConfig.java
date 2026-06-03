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
}