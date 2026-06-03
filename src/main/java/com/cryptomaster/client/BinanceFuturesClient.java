package com.cryptomaster.client;

import com.cryptomaster.config.AppConfig;
import com.cryptomaster.model.DerivativesData;
import com.cryptomaster.util.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binance USDT-M Futures (fapi) üzerinden ÜCRETSİZ türev verileri:
 * funding rate, open interest, long/short oranları, taker alım/satım oranı.
 */
@Component
public class BinanceFuturesClient {
    private static final Logger log = LoggerFactory.getLogger(BinanceFuturesClient.class);
    private final WebClient webClient;
    private final RateLimiter rateLimiter = new RateLimiter(100);

    public BinanceFuturesClient(AppConfig appConfig) {
        this.webClient = WebClient.builder()
                .baseUrl(appConfig.getBinanceFuturesRestBaseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    /** Tek çağrıda tüm sembollerin funding oranını döner (symbol -> lastFundingRate). */
    public Map<String, Double> getAllFundingRates() {
        Map<String, Double> result = new HashMap<>();
        try {
            rateLimiter.acquire();
            List<JsonNode> arr = webClient.get()
                    .uri("/fapi/v1/premiumIndex")
                    .retrieve()
                    .bodyToFlux(JsonNode.class)
                    .collectList()
                    .block();
            if (arr != null) {
                for (JsonNode n : arr) {
                    if (n.has("symbol") && n.has("lastFundingRate")) {
                        result.put(n.get("symbol").asText(), n.get("lastFundingRate").asDouble());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Funding oranları alınamadı: {}", e.getMessage());
        }
        return result;
    }

    /** Bir sembol için tüm türev metriklerini toplar. Futures'ta yoksa invalid döner. */
    public DerivativesData getDerivatives(String symbol, double fundingRate) {
        DerivativesData d = new DerivativesData(symbol);
        boolean any = false;
        try {
            d.setFundingRate(fundingRate);

            // Open Interest değişimi (son 2 x 5dk)
            JsonNode oiHist = fetchArray("/futures/data/openInterestHist",
                    Map.of("symbol", symbol, "period", "5m", "limit", "2"));
            if (oiHist != null && oiHist.size() == 2) {
                double prev = oiHist.get(0).get("sumOpenInterest").asDouble();
                double now = oiHist.get(1).get("sumOpenInterest").asDouble();
                if (prev > 0) d.setOiChangePct((now - prev) / prev * 100.0);
                any = true;
            }

            // Global (retail) hesap long/short oranı
            JsonNode gls = fetchArray("/futures/data/globalLongShortAccountRatio",
                    Map.of("symbol", symbol, "period", "5m", "limit", "1"));
            if (gls != null && gls.size() >= 1) {
                d.setGlobalLongShortRatio(gls.get(0).get("longShortRatio").asDouble());
                any = true;
            } else d.setGlobalLongShortRatio(1.0);

            // Büyük hesap (akıllı para) pozisyon long/short oranı
            JsonNode tls = fetchArray("/futures/data/topLongShortPositionRatio",
                    Map.of("symbol", symbol, "period", "5m", "limit", "1"));
            if (tls != null && tls.size() >= 1) {
                d.setTopTraderLongShortRatio(tls.get(0).get("longShortRatio").asDouble());
                any = true;
            } else d.setTopTraderLongShortRatio(1.0);

            // Taker alım/satım hacim oranı
            JsonNode tk = fetchArray("/futures/data/takerlongshortRatio",
                    Map.of("symbol", symbol, "period", "5m", "limit", "1"));
            if (tk != null && tk.size() >= 1) {
                d.setTakerBuySellRatio(tk.get(0).get("buySellRatio").asDouble());
                any = true;
            } else d.setTakerBuySellRatio(1.0);

            d.setValid(any);
        } catch (Exception e) {
            return DerivativesData.invalid(symbol);
        }
        return d;
    }

    private JsonNode fetchArray(String path, Map<String, String> params) {
        try {
            rateLimiter.acquire();
            return webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        params.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            return null;
        }
    }
}