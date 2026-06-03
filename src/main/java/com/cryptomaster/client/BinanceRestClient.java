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

    // Stablecoin / sarmalanmış token gibi istenmeyen kuyruklar (hacmi yüksek ama sinyal için anlamsız)
    private static final List<String> UNWANTED_SUFFIXES = List.of(
            "UPUSDT", "DOWNUSDT", "BULLUSDT", "BEARUSDT"
    );
    private static final Set<String> STABLECOIN_BASES = Set.of(
            "USDCUSDT", "BUSDUSDT", "DAIUSDT", "TUSDUSDT", "USDPUSDT",
            "FDUSDUSDT", "USDDUSDT", "USDJUSDT", "USTCUSDT", "EURUSDT", "AEURUSDT"
    );

    /**
     * Tüm USDT çiftlerini 24 saatlik USDT (quote) hacmine göre sıralayıp,
     * en yüksek hacimli (en popüler / likit) top N tanesini getirir.
     * Yüksek hacim = aktif işlem gören coin → delist olmuş coinler otomatik elenir.
     */
    public Map<String, Double> getAllUSDTPairs() {
        rateLimiter.acquire();
        log.info("📡 Binance'den 24s ticker verileri çekiliyor (hacme göre sıralanacak)...");

        // /api/v3/ticker/24hr → lastPrice + quoteVolume içerir
        List<JsonNode> tickers = webClient.get()
                .uri("/api/v3/ticker/24hr")
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .collectList()
                .block();

        if (tickers == null) {
            log.warn("⚠️ Ticker verisi alınamadı.");
            return new LinkedHashMap<>();
        }

        // Her sembol için (symbol, price, quoteVolume) topla + filtrele
        record Pair(String symbol, double price, double quoteVolume) {}

        List<Pair> pairs = tickers.stream()
                .filter(t -> {
                    String s = t.get("symbol").asText();
                    if (!s.endsWith("USDT")) return false;
                    if (STABLECOIN_BASES.contains(s)) return false;
                    return UNWANTED_SUFFIXES.stream().noneMatch(s::endsWith);
                })
                .map(t -> new Pair(
                        t.get("symbol").asText(),
                        t.get("lastPrice").asDouble(),
                        t.get("quoteVolume").asDouble()   // USDT cinsinden 24s hacim
                ))
                .filter(p -> p.quoteVolume() > 0)          // işlem görmeyen / pasif çiftleri ele
                .sorted(Comparator.comparingDouble(Pair::quoteVolume).reversed())
                .limit(appConfig.getTopCoinCount())
                .toList();

        log.info("✅ {} USDT çifti içinden hacme göre en yüksek {} tanesi seçildi.",
                tickers.size(), pairs.size());

        Map<String, Double> result = new LinkedHashMap<>();
        for (Pair p : pairs) {
            result.put(p.symbol(), p.price());
        }
        return result;
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