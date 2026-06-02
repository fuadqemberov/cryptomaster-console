package com.cryptomaster.client;

import com.cryptomaster.dto.coingecko.CoinMarket;
import com.cryptomaster.exception.CoinGeckoException;
import com.cryptomaster.model.Kline;
import com.cryptomaster.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CoinGeckoClient {
    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private final WebClient webClient;
    private final RateLimiter rateLimiter;
    private final Map<String, List<Kline>> cache = new ConcurrentHashMap<>();

    public CoinGeckoClient(WebClient.Builder webClientBuilder,
                           @Value("${coingecko.api.base-url}") String baseUrl,
                           @Value("${coingecko.api.rate-limit-ms}") long rateLimitMs) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.rateLimiter = new RateLimiter(rateLimitMs);
    }

    public List<CoinMarket> getTopCoins(int count) {
        rateLimiter.acquire();
        log.info("CoinGecko'dan ilk {} coin çekiliyor...", count);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/coins/markets")
                        .queryParam("vs_currency", "usd")
                        .queryParam("order", "market_cap_desc")
                        .queryParam("per_page", count)
                        .queryParam("page", 1)
                        .queryParam("sparkline", false)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        Mono.error(new CoinGeckoException("Coin listesi hatası: " + response.statusCode())))
                .bodyToFlux(CoinMarket.class)
                .collectList()
                .block();
    }

    public List<Kline> getOHLC(String coinId, int days) {
        String cacheKey = coinId + "_" + days;
        if (cache.containsKey(cacheKey)) {
            log.debug("Cache'den {} için {} günlük veri alındı", coinId, days);
            return cache.get(cacheKey);
        }

        rateLimiter.acquire();
        log.info("{} için {} günlük OHLC verisi çekiliyor...", coinId, days);

        List<List<Object>> raw = retryOnRateLimit(() ->
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/coins/{id}/ohlc")
                                .queryParam("vs_currency", "usd")
                                .queryParam("days", days)
                                .build(coinId))
                        .retrieve()
                        .onStatus(status -> status.value() == 429,
                                response -> Mono.error(new CoinGeckoException("Rate limit aşıldı")))
                        .onStatus(HttpStatusCode::isError,
                                response -> Mono.error(new CoinGeckoException("OHLC hatası: " + response.statusCode())))
                        .bodyToMono(List.class)
                        .block()
        );

        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<Kline> klines = new ArrayList<>();
        for (Object item : raw) {
            List<Object> ohlc = (List<Object>) item;
            long timestamp = ((Number) ohlc.get(0)).longValue();
            double open = ((Number) ohlc.get(1)).doubleValue();
            double high = ((Number) ohlc.get(2)).doubleValue();
            double low = ((Number) ohlc.get(3)).doubleValue();
            double close = ((Number) ohlc.get(4)).doubleValue();
            klines.add(new Kline(timestamp, open, high, low, close, 0));
        }
        cache.put(cacheKey, klines);
        return klines;
    }

    public Map<String, Object> getCoinById(String coinId) {
        rateLimiter.acquire();
        return webClient.get()
                .uri("/coins/{id}", coinId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    // --- Rate‑limit retry helper ---
    private <T> T retryOnRateLimit(SupplierWithException<T> supplier) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return supplier.get();
            } catch (CoinGeckoException e) {
                if (e.getMessage().contains("Rate limit aşıldı")) {
                    long sleepMs = (long) (Math.pow(2, i) * 2000); // 2, 4, 8 saniye
                    log.warn("Rate limit aşıldı, {} ms bekleniyor...", sleepMs);
                    try { Thread.sleep(sleepMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    throw e; // diğer hataları direkt fırlat
                }
            }
        }
        throw new CoinGeckoException("Rate limit aşıldı, " + maxRetries + " deneme başarısız.");
    }

    @FunctionalInterface
    interface SupplierWithException<T> {
        T get() throws CoinGeckoException;
    }
}