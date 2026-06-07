package com.cryptomaster.client;

import com.cryptomaster.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tüm piyasanın zorunlu kapanışlarını (!forceOrder@arr) dinler ve
 * son N dakikadaki likidasyon hacimlerini sembol bazında tutar.
 * SELL = LONG likidasyonu (forced sell), BUY = SHORT likidasyonu (forced buy).
 */
@Component
public class LiquidationWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(LiquidationWebSocketClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AppConfig appConfig;
    private static final long WINDOW_MS = 15 * 60 * 1000L; // 15 dakikalık pencere

    private record Liq(long ts, double longUsd, double shortUsd) {}
    private final Map<String, Deque<Liq>> events = new ConcurrentHashMap<>();

    public LiquidationWebSocketClient(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public void connect() {
        String url = appConfig.getBinanceFuturesWsBaseUrl() + "/!forceOrder@arr";
        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(url), session -> {
            log.info("✅ Likidasyon stream'i bağlandı (!forceOrder@arr)");
            return session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(this::process)
                    .then();
        }).subscribe();
    }

    private void process(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            JsonNode o = root.has("o") ? root.get("o") : (root.has("data") ? root.get("data").get("o") : null);
            if (o == null) return;
            String symbol = o.get("s").asText();
            String side = o.get("S").asText();          // SELL veya BUY
            double qty = o.get("q").asDouble();
            double price = o.get("ap").asDouble() > 0 ? o.get("ap").asDouble() : o.get("p").asDouble();
            double usd = qty * price;
            long now = System.currentTimeMillis();

            double longUsd = side.equalsIgnoreCase("SELL") ? usd : 0; // long pozisyon likide oldu
            double shortUsd = side.equalsIgnoreCase("BUY") ? usd : 0; // short pozisyon likide oldu

            Deque<Liq> dq = events.computeIfAbsent(symbol, k -> new ArrayDeque<>());
            synchronized (dq) {
                dq.addLast(new Liq(now, longUsd, shortUsd));
                prune(dq, now);
            }
        } catch (Exception e) {
            // sessizce geç - tek bir bozuk mesaj akışı bozmasın
        }
    }

    private void prune(Deque<Liq> dq, long now) {
        while (!dq.isEmpty() && now - dq.peekFirst().ts() > WINDOW_MS) dq.pollFirst();
    }

    /** {longLikideUsd, shortLikideUsd} son 15 dk. */
    public double[] getLiquidations(String symbol) {
        Deque<Liq> dq = events.get(symbol);
        if (dq == null) return new double[]{0, 0};
        double l = 0, s = 0;
        long now = System.currentTimeMillis();
        synchronized (dq) {
            prune(dq, now);
            for (Liq liq : dq) { l += liq.longUsd(); s += liq.shortUsd(); }
        }
        return new double[]{l, s};
    }
}