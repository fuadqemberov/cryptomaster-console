package com.cryptomaster.client;

import com.cryptomaster.config.AppConfig;
import com.cryptomaster.model.Kline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BinanceWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppConfig appConfig;

    // Sembol -> Kline listesi (her interval için)
    private final Map<String, Map<String, List<Kline>>> klineCache = new ConcurrentHashMap<>();
    // Sembol -> Güncel fiyat
    private final Map<String, Double> priceCache = new ConcurrentHashMap<>();

    public BinanceWebSocketClient(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public Map<String, Map<String, List<Kline>>> getKlineCache() {
        return new ConcurrentHashMap<>(klineCache);
    }

    public Map<String, Double> getPriceCache() {
        return new ConcurrentHashMap<>(priceCache);
    }

    public void connect(List<String> symbols) {
        // Tüm semboller için stream adlarını oluştur
        List<String> streams = new ArrayList<>();
        for (String symbol : symbols) {
            String lowSymbol = symbol.toLowerCase();
            streams.add(lowSymbol + "@kline_1m");  // 1 dakikalık mum
            streams.add(lowSymbol + "@trade");      // anlık fiyat
        }

        // Her seferde 50 stream bağlansın (Binance limiti)
        int batchSize = 50;
        for (int i = 0; i < streams.size(); i += batchSize) {
            int end = Math.min(i + batchSize, streams.size());
            List<String> batch = streams.subList(i, end);
            String streamParam = String.join("/", batch);
            String wsUrl = appConfig.getBinanceWsBaseUrl() + "/" + streamParam;

            WebSocketClient client = new ReactorNettyWebSocketClient();
            client.execute(URI.create(wsUrl), session -> {
                log.info("✅ WebSocket bağlantısı kuruldu: {} stream", batch.size());
                return session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(this::processMessage)
                        .then();
            }).subscribe();
        }
        log.info("🚀 Tüm WebSocket bağlantıları başlatıldı. {} sembol izleniyor.", symbols.size());
    }

    private void processMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("stream")) {
                String streamName = node.get("stream").asText();
                JsonNode data = node.get("data");

                if (streamName.contains("@kline")) {
                    processKlineMessage(streamName, data);
                } else if (streamName.contains("@trade")) {
                    processTradeMessage(streamName, data);
                }
            }
        } catch (Exception e) {
            log.error("WebSocket mesaj işleme hatası: {}", e.getMessage());
        }
    }

    private void processKlineMessage(String streamName, JsonNode data) {
        try {
            String symbol = streamName.replace("@kline_1m", "").toUpperCase();
            JsonNode kline = data.get("k");

            long openTime = kline.get("t").asLong();
            double open = kline.get("o").asDouble();
            double high = kline.get("h").asDouble();
            double low = kline.get("l").asDouble();
            double close = kline.get("c").asDouble();
            double volume = kline.get("v").asDouble();
            long closeTime = kline.get("T").asLong();

            Kline newKline = new Kline(openTime, open, high, low, close, volume);
            newKline.setCloseTime(closeTime);
            newKline.setInterval("1m");

            klineCache.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
            klineCache.get(symbol).computeIfAbsent("1m", k -> Collections.synchronizedList(new ArrayList<>()));

            List<Kline> klines = klineCache.get(symbol).get("1m");
            // Aynı mumu tekrar ekleme (openTime'a göre kontrol)
            if (klines.isEmpty() || klines.get(klines.size() - 1).getOpenTime() != openTime) {
                klines.add(newKline);
                // 100'den fazla mum tutma
                if (klines.size() > 200) {
                    klines.remove(0);
                }
            } else {
                // Mevcut mumu güncelle
                klines.set(klines.size() - 1, newKline);
            }

        } catch (Exception e) {
            log.error("Kline işleme hatası: {}", e.getMessage());
        }
    }

    private void processTradeMessage(String streamName, JsonNode data) {
        try {
            String symbol = streamName.replace("@trade", "").toUpperCase();
            double price = data.get("p").asDouble();
            priceCache.put(symbol, price);
        } catch (Exception e) {
            log.error("Trade işleme hatası: {}", e.getMessage());
        }
    }
}