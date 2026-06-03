package com.cryptomaster.service.analysis;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.CandlePattern;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Çoklu zaman dilimi + indikatör uyumundan 0-100 yönlü güven skoru üretir.
 * 50 = nötr, >50 boğa, <50 ayı.
 * Yüksek zaman dilimi (1d) hem ağırlıklı hem de filtre olarak çalışır.
 */
@Component
public class ConfluenceEngine {

    // Zaman dilimi ağırlıkları (yüksek TF daha belirleyici)
    private static final Map<String, Double> TF_WEIGHTS = Map.of(
            "1h", 1.0,
            "4h", 2.0,
            "1d", 3.0
    );

    public int calculateConfluence(AnalysisResult analysisResult) {
        Map<String, AnalysisResult.TimeframeAnalysis> analyses = analysisResult.getTimeframeAnalyses();
        if (analyses == null || analyses.isEmpty()) return 50;

        double totalBull = 0;
        double totalBear = 0;

        for (Map.Entry<String, AnalysisResult.TimeframeAnalysis> e : analyses.entrySet()) {
            String interval = e.getKey();
            AnalysisResult.TimeframeAnalysis tf = e.getValue();
            if (tf.getIndicators() == null) continue;

            double[] bullBear = scoreTimeframe(tf);
            double weight = TF_WEIGHTS.getOrDefault(interval, 1.0);
            totalBull += bullBear[0] * weight;
            totalBear += bullBear[1] * weight;
        }

        double total = totalBull + totalBear;
        if (total == 0) return 50;
        double score = (totalBull / total) * 100.0;

        // ---- 1d FİLTRESİ: yüksek zaman dilimi güçlü trendine ters sinyali kıs ----
        AnalysisResult.TimeframeAnalysis daily = analyses.get("1d");
        if (daily != null && daily.getIndicators() != null) {
            double adxD = daily.getIndicators().getOrDefault("ADX14", 0.0);
            String trendD = daily.getTrend();
            if (adxD >= 25 && "DOWNTREND".equals(trendD)) {
                score = Math.min(score, 55); // güçlü günlük düşüşte agresif LONG'a izin verme
            } else if (adxD >= 25 && "UPTREND".equals(trendD)) {
                score = Math.max(score, 45); // güçlü günlük yükselişte agresif SHORT'a izin verme
            }
        }

        return (int) Math.round(Math.min(100, Math.max(0, score)));
    }

    /** Tek bir zaman dilimi için {bullScore, bearScore} üretir. */
    private double[] scoreTimeframe(AnalysisResult.TimeframeAnalysis tf) {
        Map<String, Double> ind = tf.getIndicators();
        double bull = 0, bear = 0;

        double adx = ind.getOrDefault("ADX14", 0.0);
        boolean trending = adx >= 25;

        // 1) Trend (EMA dizilimi)
        if ("UPTREND".equals(tf.getTrend())) bull += 1.5;
        else if ("DOWNTREND".equals(tf.getTrend())) bear += 1.5;

        // 2) EMA200 rejimi
        double ema200 = ind.getOrDefault("EMA200", 0.0);
        double ema20 = ind.getOrDefault("EMA20", 0.0);
        if (ema200 > 0 && ema20 > 0) {
            if (ema20 > ema200) bull += 1.0; else bear += 1.0;
        }

        // 3) SuperTrend
        double st = ind.getOrDefault("SUPERTREND_DIR", 0.0);
        if (st > 0) bull += 1.5; else if (st < 0) bear += 1.5;

        // 4) +DI / -DI (yön gücü)
        double plusDi = ind.getOrDefault("PLUS_DI", 0.0);
        double minusDi = ind.getOrDefault("MINUS_DI", 0.0);
        if (plusDi > minusDi) bull += 1.0; else if (minusDi > plusDi) bear += 1.0;

        // 5) MACD histogram
        double macd = ind.getOrDefault("MACD_hist", 0.0);
        if (macd > 0) bull += 1.0; else if (macd < 0) bear += 1.0;

        // 6) RSI — rejime duyarlı
        double rsi = ind.getOrDefault("RSI14", 50.0);
        if (trending) {
            if (rsi > 50) bull += 1.0; else bear += 1.0;        // trendde momentum
        } else {
            if (rsi < 30) bull += 1.0;                           // range'de tepki alımı
            else if (rsi > 70) bear += 1.0;
        }

        // 7) Stochastic RSI
        double stoch = ind.getOrDefault("STOCH_RSI", 50.0);
        if (stoch < 20) bull += 0.5; else if (stoch > 80) bear += 0.5;

        // 8) Hacim teyidi — baskın yönü güçlendirir
        double rvol = ind.getOrDefault("RVOL", 1.0);
        double obvSlope = ind.getOrDefault("OBV_SLOPE", 0.0);
        if (rvol > 1.2) {
            if (obvSlope > 0) bull += 1.0; else if (obvSlope < 0) bear += 1.0;
        }

        // 9) VWAP konumu
        double vwapRel = ind.getOrDefault("VWAP_REL", 0.0);
        if (vwapRel > 0) bull += 0.5; else if (vwapRel < 0) bear += 0.5;

        // 10) Divergence (güçlü dönüş sinyali)
        double div = ind.getOrDefault("DIVERGENCE", 0.0);
        if (div > 0) bull += 1.5; else if (div < 0) bear += 1.5;

        // 11) Bollinger uç bölgeler
        double bbPos = ind.getOrDefault("BB_position", 0.5);
        if (bbPos > 0.9) bear += 0.5; else if (bbPos < 0.1) bull += 0.5;

        // 12) Mum formasyonları
        List<CandlePattern> patterns = tf.getPatterns();
        if (patterns != null) {
            for (CandlePattern p : patterns) {
                switch (p) {
                    case HAMMER, BULLISH_ENGULFING, MORNING_STAR, THREE_WHITE_SOLDIERS, PIERCING_LINE, HARAMI_BULLISH ->
                            bull += 0.75;
                    case SHOOTING_STAR, BEARISH_ENGULFING, EVENING_STAR, THREE_BLACK_CROWS, DARK_CLOUD_COVER, HARAMI_BEARISH ->
                            bear += 0.75;
                    default -> { /* DOJI nötr */ }
                }
            }
        }

        return new double[]{bull, bear};
    }
}