package com.cryptomaster.service.strategy;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.DerivativesData;
import com.cryptomaster.model.MarketContext;
import com.cryptomaster.model.TradingSignal;
import com.cryptomaster.service.analysis.ConfluenceEngine;
import com.cryptomaster.service.analysis.DerivativesAnalyzer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SignalGenerationService {

    private final ConfluenceEngine confluenceEngine;
    private final DerivativesAnalyzer derivativesAnalyzer;

    public SignalGenerationService(ConfluenceEngine confluenceEngine, DerivativesAnalyzer derivativesAnalyzer) {
        this.confluenceEngine = confluenceEngine;
        this.derivativesAnalyzer = derivativesAnalyzer;
    }

    /** Geriye dönük uyumluluk: sadece teknik analiz. */
    public TradingSignal generateSignal(String symbol, double currentPrice, AnalysisResult analysisResult) {
        return generateSignal(symbol, currentPrice, analysisResult, null, null);
    }

    public TradingSignal generateSignal(String symbol, double currentPrice, AnalysisResult analysisResult,
                                        DerivativesData derivatives, MarketContext context) {
        int technical = confluenceEngine.calculateConfluence(analysisResult);

        // ---- Türev baskısı ile harmanla ----
        DerivativesAnalyzer.Result derivRes = derivativesAnalyzer.analyze(derivatives);
        boolean derivValid = derivatives != null && derivatives.isValid();
        double confidenceD = derivValid
                ? technical * 0.65 + derivRes.score * 0.35
                : technical;

        // ---- Global bağlam yanlılığı (Fear&Greed + BTC rejimi) ----
        if (context != null) {
            double fg = context.getFearGreed();
            if (fg < 20) confidenceD += 4;        // aşırı korku → kontraryan boğa
            else if (fg > 80) confidenceD -= 4;   // aşırı açgözlülük → kontraryan ayı
            confidenceD += context.getBtcRegime() * 3; // altlar BTC'yi takip eder
        }

        int confidence = (int) Math.round(Math.min(100, Math.max(0, confidenceD)));

        // ---- Rejim filtresi: trend yoksa eşiği sıkılaştır (whipsaw'ı azaltır) ----
        double adx4h = getIndicator(analysisResult, "4h", "ADX14", 0);
        double adx1d = getIndicator(analysisResult, "1d", "ADX14", 0);
        double maxDivergence = Math.max(
                Math.abs(getIndicator(analysisResult, "4h", "DIVERGENCE", 0)),
                Math.abs(getIndicator(analysisResult, "1d", "DIVERGENCE", 0)));
        boolean noTrend = adx4h < 18 && adx1d < 18 && maxDivergence == 0;

        int longTh = noTrend ? 70 : 65;
        int shortTh = noTrend ? 30 : 35;

        String direction;
        if (confidence >= longTh) direction = "LONG";
        else if (confidence <= shortTh) direction = "SHORT";
        else direction = "NEUTRAL";

        double atr = getAtrFromAnalysis(analysisResult);

        // ---- Yapı tabanlı (destek/direnç + ATR) SL/TP, R-katlı hedefler ----
        double entry = currentPrice;
        double sl, tp1, tp2;
        double support = getIndicator(analysisResult, "4h", "SUPPORT", entry - atr * 2);
        double resistance = getIndicator(analysisResult, "4h", "RESISTANCE", entry + atr * 2);

        if (direction.equals("LONG")) {
            double structuralSl = support - atr * 0.3;
            // SL en az 0.8 ATR, en fazla 3 ATR uzaklıkta olsun
            double dist = entry - structuralSl;
            if (dist < atr * 0.8 || dist > atr * 3) structuralSl = entry - atr * 1.8;
            sl = structuralSl;
            double risk = Math.max(entry - sl, atr * 0.5);
            tp1 = entry + risk * 1.5;
            tp2 = entry + risk * 3.0;
        } else if (direction.equals("SHORT")) {
            double structuralSl = resistance + atr * 0.3;
            double dist = structuralSl - entry;
            if (dist < atr * 0.8 || dist > atr * 3) structuralSl = entry + atr * 1.8;
            sl = structuralSl;
            double risk = Math.max(sl - entry, atr * 0.5);
            tp1 = entry - risk * 1.5;
            tp2 = entry - risk * 3.0;
        } else {
            sl = entry;
            tp1 = entry;
            tp2 = entry;
        }

        // ---- Kaldıraç: güven + oynaklık + trend gücüne göre, üst sınır 10x ----
        int leverage = 1;
        if (!direction.equals("NEUTRAL") && currentPrice > 0) {
            double volatility = atr / currentPrice;
            double maxAdx = Math.max(adx4h, adx1d);
            if (confidence >= 80 && volatility < 0.04 && maxAdx >= 25) leverage = 10;
            else if (confidence >= 75 && volatility < 0.06 && maxAdx >= 22) leverage = 7;
            else if (confidence >= 70 && volatility < 0.08) leverage = 5;
            else if (confidence >= 60 && volatility < 0.12) leverage = 3;
            else leverage = 2;
        }

        List<String> reasons = generateDetailedReasons(analysisResult, currentPrice, direction, confidence,
                derivRes.reasons, context);

        double rr = direction.equals("NEUTRAL") ? 0 :
                Math.abs(tp1 - entry) / Math.max(Math.abs(entry - sl), 1e-9);
        String summary = direction.equals("NEUTRAL")
                ? String.format("NEUTRAL — net sinyal yok (güven: %d%%)", confidence)
                : String.format("%s sinyali (güven: %d%%, R:R≈1:%.1f, kaldıraç: %dx)",
                direction, confidence, rr, leverage);

        return new TradingSignal(
                symbol, direction, currentPrice, entry, sl, tp1, tp2,
                confidence, leverage, summary, reasons, LocalDateTime.now()
        );
    }

    private double getIndicator(AnalysisResult ar, String interval, String key, double def) {
        if (ar.getTimeframeAnalyses() == null) return def;
        AnalysisResult.TimeframeAnalysis tf = ar.getTimeframeAnalyses().get(interval);
        if (tf == null || tf.getIndicators() == null) return def;
        return tf.getIndicators().getOrDefault(key, def);
    }

    private double getAtrFromAnalysis(AnalysisResult analysisResult) {
        for (String interval : List.of("4h", "1d", "1h")) {
            double v = getIndicator(analysisResult, interval, "ATR14", -1);
            if (v > 0) return v;
        }
        return 1;
    }

    private List<String> generateDetailedReasons(AnalysisResult analysisResult, double currentPrice,
                                                 String direction, int confidence,
                                                 List<String> derivReasons, MarketContext context) {
        List<String> reasons = new ArrayList<>();
        Map<String, AnalysisResult.TimeframeAnalysis> tfMap = analysisResult.getTimeframeAnalyses();
        if (tfMap == null) return reasons;

        for (Map.Entry<String, AnalysisResult.TimeframeAnalysis> entry : tfMap.entrySet()) {
            String interval = entry.getKey();
            AnalysisResult.TimeframeAnalysis tf = entry.getValue();

            reasons.add(String.format("━━━ %s Zaman Dilimi ━━━", interval));

            if (tf.getTrend() != null) {
                String trendEmoji = switch (tf.getTrend()) {
                    case "UPTREND" -> "🟢";
                    case "DOWNTREND" -> "🔴";
                    default -> "🟡";
                };
                String trendYorum = switch (tf.getTrend()) {
                    case "UPTREND" -> "Yükseliş trendi - Alım baskısı";
                    case "DOWNTREND" -> "Düşüş trendi - Satış baskısı";
                    default -> "Yatay seyir - Yön belirsiz";
                };
                reasons.add(String.format("  %s Trend: %s (Güç: %.0f/100) → %s",
                        trendEmoji, tf.getTrend(), tf.getTrendStrength(), trendYorum));
            }

            if (tf.getIndicators() != null) {
                Map<String, Double> ind = tf.getIndicators();
                reasons.add("  📊 İNDİKATÖRLER:");

                double rsi = ind.getOrDefault("RSI14", 50.0);
                String rsiYorum = rsi > 70 ? "🔥 AŞIRI ALIM" : rsi < 30 ? "❄️ AŞIRI SATIM" :
                                                              rsi > 50 ? "✅ Boğa bölgesi" : "⚠️ Ayı bölgesi";
                reasons.add(String.format("     • RSI(14): %.1f → %s", rsi, rsiYorum));

                double stoch = ind.getOrDefault("STOCH_RSI", 50.0);
                reasons.add(String.format("     • StochRSI: %.0f → %s", stoch,
                        stoch > 80 ? "aşırı alım" : stoch < 20 ? "aşırı satım" : "nötr"));

                double macdHist = ind.getOrDefault("MACD_hist", 0.0);
                reasons.add(String.format("     • MACD Hist: %.4f → %s", macdHist,
                        macdHist > 0 ? "🟢 AL" : macdHist < 0 ? "🔴 SAT" : "⚪ nötr"));

                double adx = ind.getOrDefault("ADX14", 0.0);
                double plusDi = ind.getOrDefault("PLUS_DI", 0.0);
                double minusDi = ind.getOrDefault("MINUS_DI", 0.0);
                reasons.add(String.format("     • ADX: %.1f (%s) | +DI %.1f / -DI %.1f → %s",
                        adx, adx >= 25 ? "güçlü trend" : "zayıf/yatay", plusDi, minusDi,
                        plusDi > minusDi ? "🟢 alıcı baskın" : "🔴 satıcı baskın"));

                double st = ind.getOrDefault("SUPERTREND_DIR", 0.0);
                reasons.add(String.format("     • SuperTrend: %s", st > 0 ? "🟢 YUKARI" : st < 0 ? "🔴 AŞAĞI" : "⚪ nötr"));

                double ema200 = ind.getOrDefault("EMA200", 0.0);
                reasons.add(String.format("     • EMA200: %.4f → %s", ema200,
                        currentPrice > ema200 ? "Fiyat üstünde ✅ (boğa rejimi)" : "Fiyat altında ❌ (ayı rejimi)"));

                double ema20 = ind.getOrDefault("EMA20", 0.0);
                reasons.add(String.format("     • EMA20: %.4f → %s", ema20,
                        currentPrice > ema20 ? "Fiyat üstünde ✅" : "Fiyat altında ❌"));

                double rvol = ind.getOrDefault("RVOL", 1.0);
                double obvSlope = ind.getOrDefault("OBV_SLOPE", 0.0);
                reasons.add(String.format("     • Hacim: RVOL %.2fx (%s) | OBV %s", rvol,
                        rvol > 1.2 ? "yüksek - teyit" : "normal",
                        obvSlope > 0 ? "📈 birikim" : obvSlope < 0 ? "📉 dağıtım" : "yatay"));

                double vwapRel = ind.getOrDefault("VWAP_REL", 0.0);
                reasons.add(String.format("     • VWAP: fiyat %s", vwapRel > 0 ? "üstünde ✅" : vwapRel < 0 ? "altında ❌" : "üzerinde"));

                double div = ind.getOrDefault("DIVERGENCE", 0.0);
                if (div != 0) {
                    reasons.add(String.format("     • ⚡ DIVERGENCE: %s",
                            div > 0 ? "🟢 BOĞA uyumsuzluğu (dönüş olası)" : "🔴 AYI uyumsuzluğu (dönüş olası)"));
                }

                double bbPos = ind.getOrDefault("BB_position", 0.5);
                reasons.add(String.format("     • Bollinger %%B: %.2f → %s", bbPos,
                        bbPos > 0.9 ? "üst bant - aşırı alım" : bbPos < 0.1 ? "alt bant - aşırı satım" : "normal"));

                double support = ind.getOrDefault("SUPPORT", 0.0);
                double resistance = ind.getOrDefault("RESISTANCE", 0.0);
                reasons.add(String.format("     • Destek: %.4f | Direnç: %.4f", support, resistance));
            }

            if (tf.getPatterns() != null && !tf.getPatterns().isEmpty()) {
                reasons.add("  🕯️ MUM FORMASYONLARI:");
                for (var pattern : tf.getPatterns()) {
                    String patternYorum = switch (pattern) {
                        case HAMMER -> "🟢 boğa dönüş";
                        case SHOOTING_STAR -> "🔴 ayı dönüş";
                        case BULLISH_ENGULFING -> "🟢 güçlü AL";
                        case BEARISH_ENGULFING -> "🔴 güçlü SAT";
                        case DOJI -> "⚪ kararsızlık";
                        case MORNING_STAR -> "🟢 yükseliş başlangıcı";
                        case EVENING_STAR -> "🔴 düşüş başlangıcı";
                        case THREE_WHITE_SOLDIERS -> "🟢 güçlü yükseliş";
                        case THREE_BLACK_CROWS -> "🔴 güçlü düşüş";
                        case PIERCING_LINE -> "🟢 boğa dönüş";
                        case DARK_CLOUD_COVER -> "🔴 ayı dönüş";
                        case HARAMI_BULLISH -> "🟢 olası dönüş";
                        case HARAMI_BEARISH -> "🔴 olası dönüş";
                    };
                    reasons.add(String.format("     • %s → %s", pattern.name(), patternYorum));
                }
            }
        }

        reasons.add("━━━ TÜREV & GLOBAL BAĞLAM ━━━");
        if (derivReasons != null) reasons.addAll(derivReasons);
        if (context != null) {
            String btcTxt = context.getBtcRegime() > 0 ? "🟢 boğa" : context.getBtcRegime() < 0 ? "🔴 ayı" : "⚪ nötr";
            reasons.add(String.format("  🌐 Fear&Greed: %.0f (%s) | BTC rejimi: %s",
                    context.getFearGreed(), context.getFearGreedLabel(), btcTxt));
        }

        String kararEmoji = confidence >= 65 ? "🟢" : (confidence <= 35 ? "🔴" : "🟡");
        reasons.add(String.format("━━━ KARAR: %s %s (Güven: %d/100) ━━━", kararEmoji, direction, confidence));

        return reasons;
    }
}