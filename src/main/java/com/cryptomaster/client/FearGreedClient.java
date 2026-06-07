package com.cryptomaster.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** alternative.me üzerinden ÜCRETSİZ Kripto Korku & Açgözlülük endeksi. */
@Component
public class FearGreedClient {
    private static final Logger log = LoggerFactory.getLogger(FearGreedClient.class);
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.alternative.me")
            .build();

    /** {value(0-100), label}. Hata olursa {50,"Neutral"}. */
    public double[] fetchValue() {
        try {
            JsonNode root = webClient.get()
                    .uri("/fng/?limit=1")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (root != null && root.has("data") && root.get("data").size() > 0) {
                JsonNode d = root.get("data").get(0);
                return new double[]{d.get("value").asDouble(), 1};
            }
        } catch (Exception e) {
            log.warn("Fear&Greed alınamadı: {}", e.getMessage());
        }
        return new double[]{50, 0};
    }

    public String fetchLabel() {
        try {
            JsonNode root = webClient.get().uri("/fng/?limit=1")
                    .retrieve().bodyToMono(JsonNode.class).block();
            if (root != null && root.has("data") && root.get("data").size() > 0) {
                return root.get("data").get(0).get("value_classification").asText();
            }
        } catch (Exception ignored) {}
        return "Neutral";
    }
}