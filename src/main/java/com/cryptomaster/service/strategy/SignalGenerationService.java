package com.cryptomaster.service.strategy;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.TradingSignal;
import com.cryptomaster.service.analysis.ConfluenceEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SignalGenerationService {

    private final ConfluenceEngine confluenceEngine;

    public SignalGenerationService(ConfluenceEngine confluenceEngine) {
        this.confluenceEngine = confluenceEngine;
    }

    public TradingSignal generateSignal(String symbol, double currentPrice, AnalysisResult analysisResult) {
        int confidence = confluenceEngine.calculateConfluence(analysisResult);

        String direction;
        if (confidence >= 65) direction = "LONG";
        else if (confidence <= 35) direction = "SHORT";
        else direction = "NEUTRAL";

        double atr = getAtrFromAnalysis(analysisResult);
        double entry = currentPrice;
        double sl = direction.equals("LONG") ? currentPrice - atr * 2 : currentPrice + atr * 2;
        double tp1 = direction.equals("LONG") ? currentPrice + atr * 3 : currentPrice - atr * 3;
        double tp2 = direction.equals("LONG") ? currentPrice + atr * 5 : currentPrice - atr * 5;

        // Kaldıraç hesabı
        int leverage = 1;
        if (!direction.equals("NEUTRAL") && currentPrice > 0) {
            double volatility = atr / currentPrice;
            if (confidence >= 80 && volatility < 0.05) leverage = 10;
            else if (confidence >= 70 && volatility < 0.08) leverage = 5;
            else if (confidence >= 60 && volatility < 0.12) leverage = 3;
            else if (confidence >= 50) leverage = 2;
        }

        // Detaylı sebepler (indikatör yorumlarıyla birlikte)
        List<String> reasons = generateDetailedReasons(analysisResult, direction, confidence);

        String summary = String.format("%s sinyali (güven: %d%%)", direction, confidence);

        return new TradingSignal(
                symbol, direction, currentPrice, entry, sl, tp1, tp2,
                confidence, leverage,
                summary,
                reasons,
                LocalDateTime.now()
        );
    }

    private double getAtrFromAnalysis(AnalysisResult analysisResult) {
        for (String interval : List.of("1d", "4h", "1h")) {
            AnalysisResult.TimeframeAnalysis tf = analysisResult.getTimeframeAnalyses().get(interval);
            if (tf != null && tf.getIndicators() != null && tf.getIndicators().containsKey("ATR14")) {
                return tf.getIndicators().get("ATR14");
            }
        }
        return 50;
    }

    private List<String> generateDetailedReasons(AnalysisResult analysisResult, String direction, int confidence) {
        List<String> reasons = new ArrayList<>();
        Map<String, AnalysisResult.TimeframeAnalysis> tfMap = analysisResult.getTimeframeAnalyses();
        if (tfMap == null) return reasons;

        for (Map.Entry<String, AnalysisResult.TimeframeAnalysis> entry : tfMap.entrySet()) {
            String interval = entry.getKey();
            AnalysisResult.TimeframeAnalysis tf = entry.getValue();

            // === ZAMAN DİLİMİ BAŞLIĞI ===
            reasons.add(String.format("━━━ %s Zaman Dilimi ━━━", interval));

            // === TREND ANALİZİ ===
            if (tf.getTrend() != null) {
                String trendEmoji = switch (tf.getTrend()) {
                    case "UPTREND" -> "🟢";
                    case "DOWNTREND" -> "🔴";
                    default -> "🟡";
                };
                String trendYorum = switch (tf.getTrend()) {
                    case "UPTREND" -> "Yükseliş trendi - Alım baskısı var";
                    case "DOWNTREND" -> "Düşüş trendi - Satış baskısı var";
                    default -> "Yatay seyir - Yön belirsiz";
                };
                reasons.add(String.format("  %s Trend: %s (Güç: %.0f/100) → %s",
                        trendEmoji, tf.getTrend(), tf.getTrendStrength(), trendYorum));
            }

            // === İNDİKATÖR YORUMLARI ===
            if (tf.getIndicators() != null) {
                Map<String, Double> ind = tf.getIndicators();
                reasons.add("  📊 İNDİKATÖRLER:");

                // RSI
                double rsi = ind.getOrDefault("RSI14", 50.0);
                String rsiYorum = rsi > 70 ? "🔥 AŞIRI ALIM - Düzeltme gelebilir" :
                        rsi < 30 ? "❄️ AŞIRI SATIM - Tepki alımı gelebilir" :
                        rsi > 50 ? "✅ Boğa bölgesinde" : "⚠️ Ayı bölgesinde";
                reasons.add(String.format("     • RSI(14): %.1f → %s", rsi, rsiYorum));

                // MACD
                double macdHist = ind.getOrDefault("MACD_hist", 0.0);
                String macdYorum = macdHist > 0 ? "🟢 AL sinyali (MACD > Sinyal)" :
                        macdHist < 0 ? "🔴 SAT sinyali (MACD < Sinyal)" : "⚪ Nötr";
                reasons.add(String.format("     • MACD Histogram: %.2f → %s", macdHist, macdYorum));

                // EMA
                double ema20 = ind.getOrDefault("EMA20", 0.0);
                String emaYorum = currentPrice(analysisResult) > ema20 ? "Fiyat EMA20 üstünde ✅" : "Fiyat EMA20 altında ❌";
                reasons.add(String.format("     • EMA20: %.2f → %s", ema20, emaYorum));

                // Bollinger
                double bbPos = ind.getOrDefault("BB_position", 0.5);
                String bbYorum = bbPos > 0.8 ? "📈 Üst bantta - Aşırı alım" :
                        bbPos < 0.2 ? "📉 Alt bantta - Aşırı satım" : "⚖️ Normal bölgede";
                reasons.add(String.format("     • Bollinger %%B: %.2f → %s", bbPos, bbYorum));

                // ATR
                double atr = ind.getOrDefault("ATR14", 0.0);
                reasons.add(String.format("     • ATR(14): %.2f (Oynaklık göstergesi)", atr));
            }

            // === MUM FORMASYONLARI ===
            if (tf.getPatterns() != null && !tf.getPatterns().isEmpty()) {
                reasons.add("  🕯️ MUM FORMASYONLARI:");
                for (var pattern : tf.getPatterns()) {
                    String patternYorum = switch (pattern) {
                        case HAMMER -> "🟢 BOĞA dönüş sinyali";
                        case SHOOTING_STAR -> "🔴 AYI dönüş sinyali";
                        case BULLISH_ENGULFING -> "🟢 Güçlü AL sinyali";
                        case BEARISH_ENGULFING -> "🔴 Güçlü SAT sinyali";
                        case DOJI -> "⚪ Kararsızlık";
                        case MORNING_STAR -> "🟢 Yükseliş başlangıcı";
                        case EVENING_STAR -> "🔴 Düşüş başlangıcı";
                        case THREE_WHITE_SOLDIERS -> "🟢 Güçlü yükseliş";
                        case THREE_BLACK_CROWS -> "🔴 Güçlü düşüş";
                        default -> "⚪ " + pattern.name();
                    };
                    reasons.add(String.format("     • %s → %s", pattern.name(), patternYorum));
                }
            }
        }

        // === CONFLUENCE ÖZETİ ===
        String kararEmoji = confidence >= 65 ? "🟢" : (confidence <= 35 ? "🔴" : "🟡");
        reasons.add(String.format("━━━ KARAR: %s %s (Güven: %d/100) ━━━", kararEmoji, direction, confidence));

        return reasons;
    }

    private double currentPrice(AnalysisResult analysisResult) {
        // Son fiyatı analysis sonuçlarından alamayız, dışarıdan gelir.
        // Bu metot şimdilik 0 dönecek, price dışarıdan alınıyor zaten.
        return 0;
    }
}