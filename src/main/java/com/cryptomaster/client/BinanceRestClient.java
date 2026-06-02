package com.cryptomaster.client;

import com.cryptomaster.config.AppConfig;
import com.cryptomaster.model.Kline;
import com.cryptomaster.util.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class BinanceRestClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceRestClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimiter rateLimiter;
    private final AppConfig appConfig;

    public BinanceRestClient(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.webClient = WebClient.builder()
                .baseUrl(appConfig.getBinanceRestBaseUrl())
                .build();
        this.rateLimiter = new RateLimiter(200); // 200ms bekleme
    }

    /**
     * Tüm USDT çiftlerini ve son fiyatlarını getirir
     */
    public Map<String, Double> getAllUSDTPairs() {
        rateLimiter.acquire();
        log.info("📡 Binance'den tüm USDT çiftleri çekiliyor...");

        List<JsonNode> tickers = webClient.get()
                .uri("/api/v3/ticker/price")
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .collectList()
                .block();

        Map<String, Double> result = new LinkedHashMap<>();
        if (tickers != null) {
            for (JsonNode ticker : tickers) {
                String symbol = ticker.get("symbol").asText();
                if (symbol.endsWith("USDT")) {
                    double price = ticker.get("price").asDouble();
                    result.put(symbol, price);
                }
            }
        }

        // Hacme göre sırala ve top N al
        log.info("✅ {} USDT çifti bulundu. İlk {} tanesi alınıyor...", result.size(), appConfig.getTopCoinCount());
        return result.entrySet().stream()
                .limit(appConfig.getTopCoinCount())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Geçmiş OHLC verisini çeker
     */
    public List<Kline> getKlines(String symbol, String interval, int limit) {
        rateLimiter.acquire();
        List<List<Object>> raw = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<Kline> klines = new ArrayList<>();
        for (List<Object> item : raw) {
            Kline kline = new Kline();
            kline.setOpenTime(((Number) item.get(0)).longValue());
            kline.setOpen(Double.parseDouble(item.get(1).toString()));
            kline.setHigh(Double.parseDouble(item.get(2).toString()));
            kline.setLow(Double.parseDouble(item.get(3).toString()));
            kline.setClose(Double.parseDouble(item.get(4).toString()));
            kline.setVolume(Double.parseDouble(item.get(5).toString()));
            kline.setCloseTime(((Number) item.get(6)).longValue());
            kline.setInterval(interval);
            klines.add(kline);
        }
        return klines;
    }
}