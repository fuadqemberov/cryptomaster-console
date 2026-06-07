package com.cryptomaster.service.strategy;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.CandlePattern;
import com.cryptomaster.model.ScalpSignal;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 5-10 dakikalık scalping sinyalleri üretir.
 *
 * Felsefe (çoklu zaman dilimi onayı):
 *   • 15m  → ÜST TREND FİLTRESİ. Trende karşı scalp açmayı cezalandırır.
 *   • 5m   → BİRİNCİL KURULUM. VWAP/EMA/SuperTrend/MACD/RSI + hacim.
 *   • 1m   → GİRİŞ TETİĞİ. Geri çekilmeden dönüş + VWAP geri alımı.
 *
 * Sadece "A+ kurulum" (yüksek eşik) sinyalleri döner; gürültüyü kasıtlı eler.
 * Hedefler 5m ATR ile DAR tutulur → amaç birkaç mumda küçük ama hızlı kazanç.
 */
@Service
public class ScalpSignalService {

    /** Bu eşiğin altındaki kurulumlar NEUTRAL döner (gürültü filtresi). */
    private final int minConfidence;

    public ScalpSignalService(
            @org.springframework.beans.factory.annotation.Value("${cryptomaster.scalp-min-confidence:72}") int minConfidence) {
        this.minConfidence = minConfidence;
    }

    public ScalpSignal generate(String symbol, double price, AnalysisResult ar) {
        Map<String, AnalysisResult.TimeframeAnalysis> tfs = ar.getTimeframeAnalyses();
        if (tfs == null) return neutral(symbol, price);

        AnalysisResult.TimeframeAnalysis tf5 = tfs.get("5m");
        AnalysisResult.TimeframeAnalysis tf1 = tfs.get("1m");
        AnalysisResult.TimeframeAnalysis tf15 = tfs.get("15m");
        if (tf5 == null || tf5.getIndicators() == null) return neutral(symbol, price);

        Map<String, Double> i5 = tf5.getIndicators();
        Map<String, Double> i1 = tf1 != null ? tf1.getIndicators() : Map.of();
        Map<String, Double> i15 = tf15 != null ? tf15.getIndicators() : Map.of();

        // ---- HACİM KAPISI: ölü tahtada scalp yok (hızlı hareket için katılım şart) ----
        double rvol5 = i5.getOrDefault("RVOL", 1.0);
        double rvol1 = i1.getOrDefault("RVOL", 1.0);
        if (rvol5 < 1.10 && rvol1 < 1.30) {
            return neutral(symbol, price);
        }

        int htfBias = trendBias(tf15, i15); // +1 boğa / 0 nötr / -1 ayı (15m)

        List<String> longReasons = new ArrayList<>();
        List<String> shortReasons = new ArrayList<>();
        double longScore = scoreSide(true, i5, i1, tf5, tf1, htfBias, rvol5, rvol1, price, longReasons);
        double shortScore = scoreSide(false, i5, i1, tf5, tf1, htfBias, rvol5, rvol1, price, shortReasons);

        boolean longWins = longScore >= shortScore;
        double rawScore = longWins ? longScore : shortScore;
        int confidence = (int) Math.round(Math.min(100, Math.max(0, rawScore)));
        String direction = longWins ? "LONG" : "SHORT";
        List<String> reasons = longWins ? longReasons : shortReasons;

        if (confidence < minConfidence) {
            return neutral(symbol, price);
        }

        // ---- DAR (scalp) SL/TP: 5m ATR tabanlı ----
        double atr5 = firstPositive(i5.getOrDefault("ATR14", 0.0), price * 0.004);
        double atrPct = price > 0 ? atr5 / price : 0;
        double risk = Math.max(atr5 * 1.1, price * 0.0015); // çok dar stop'tan kaçın

        double entry = price, sl, tp1, tp2;
        if ("LONG".equals(direction)) {
            sl = entry - risk;
            tp1 = entry + risk * 1.2;   // hızlı ~1:1.2
            tp2 = entry + risk * 2.0;
        } else {
            sl = entry + risk;
            tp1 = entry - risk * 1.2;
            tp2 = entry - risk * 2.0;
        }
        double rr = Math.abs(tp1 - entry) / Math.max(Math.abs(entry - sl), 1e-9);

        int leverage = suggestLeverage(confidence, atrPct);
        String hold = "~5-10 dk (TP1'e ulaşılmazsa süre dolunca kapat)";

        reasons.add(0, String.format("⚡ SCALP %s — güven %d%% | R:R≈1:%.1f | kaldıraç %dx | oynaklık %.2f%%",
                direction, confidence, rr, leverage, atrPct * 100));
        reasons.add(String.format("🎯 Plan: Giriş %.6f | SL %.6f | TP1 %.6f | TP2 %.6f", entry, sl, tp1, tp2));
        reasons.add("⏱️ Süre: " + hold + " | Stop'a sıkı uy, TP1'de yarı kapat → SL'yi girişe çek.");

        return new ScalpSignal(symbol, direction, price, entry, sl, tp1, tp2,
                confidence, leverage, rr, atrPct, hold, reasons, LocalDateTime.now());
    }

    /**
     * Bir yön için (long=true / short=false) 0-100+ ham skor üretir ve gerekçeleri doldurur.
     * Skor bileşenleri ideal kurulumda ~100'e ulaşır.
     */
    private double scoreSide(boolean longSide,
                             Map<String, Double> i5, Map<String, Double> i1,
                             AnalysisResult.TimeframeAnalysis tf5, AnalysisResult.TimeframeAnalysis tf1,
                             int htfBias, double rvol5, double rvol1,
                             double price, List<String> reasons) {
        double s = 0;
        int sign = longSide ? 1 : -1;

        // ================= 5m BİRİNCİL KURULUM (≈45p) =================
        double vwapRel5 = i5.getOrDefault("VWAP_REL", 0.0);
        if (vwapRel5 * sign > 0) { s += 8; reasons.add("  5m: fiyat VWAP " + side(longSide, "üstünde", "altında") + " ✅"); }

        double ema20 = i5.getOrDefault("EMA20", 0.0), ema50 = i5.getOrDefault("EMA50", 0.0);
        if (ema20 > 0 && ema50 > 0 && (ema20 - ema50) * sign > 0) {
            s += 8; reasons.add("  5m: EMA20 " + side(longSide, "> EMA50 (kısa MA yukarı)", "< EMA50 (kısa MA aşağı)"));
        }

        double st5 = i5.getOrDefault("SUPERTREND_DIR", 0.0);
        if (st5 * sign > 0) { s += 8; reasons.add("  5m: SuperTrend " + side(longSide, "YUKARI 🟢", "AŞAĞI 🔴")); }

        double macd5 = i5.getOrDefault("MACD_hist", 0.0);
        if (macd5 * sign > 0) { s += 6; reasons.add("  5m: MACD momentum " + side(longSide, "pozitif", "negatif")); }

        double rsi5 = i5.getOrDefault("RSI14", 50.0);
        if (longSide) {
            if (rsi5 > 50 && rsi5 < 68) { s += 7; reasons.add(String.format("  5m: RSI %.0f (boğa momentumu, aşırı alım değil)", rsi5)); }
            else if (rsi5 >= 72) { s -= 6; reasons.add(String.format("  5m: ⚠️ RSI %.0f aşırı alım — geç giriş riski", rsi5)); }
        } else {
            if (rsi5 < 50 && rsi5 > 32) { s += 7; reasons.add(String.format("  5m: RSI %.0f (ayı momentumu, aşırı satım değil)", rsi5)); }
            else if (rsi5 <= 28) { s -= 6; reasons.add(String.format("  5m: ⚠️ RSI %.0f aşırı satım — geç giriş riski", rsi5)); }
        }

        if (rvol5 > 1.3) { s += 8; reasons.add(String.format("  5m: RVOL %.2fx (güçlü hacim teyidi)", rvol5)); }
        else if (rvol5 > 1.1) { s += 4; reasons.add(String.format("  5m: RVOL %.2fx (yeterli hacim)", rvol5)); }

        // ================= 1m GİRİŞ TETİĞİ (≈25p) =================
        if (!i1.isEmpty()) {
            double stoch1 = i1.getOrDefault("STOCH_RSI", 50.0);
            // Geri çekilmeden dönüş: long'da aşırı satımdan, short'ta aşırı alımdan dönüş ideal giriş
            if (longSide && stoch1 < 35) { s += 8; reasons.add(String.format("  1m: StochRSI %.0f düşük → dönüş/giriş bölgesi", stoch1)); }
            else if (!longSide && stoch1 > 65) { s += 8; reasons.add(String.format("  1m: StochRSI %.0f yüksek → dönüş/giriş bölgesi", stoch1)); }

            double vwapRel1 = i1.getOrDefault("VWAP_REL", 0.0);
            if (vwapRel1 * sign > 0) { s += 6; reasons.add("  1m: VWAP " + side(longSide, "geri alındı ✅", "kaybedildi ✅")); }

            double macd1 = i1.getOrDefault("MACD_hist", 0.0);
            if (macd1 * sign > 0) { s += 5; reasons.add("  1m: MACD tetik " + side(longSide, "yukarı", "aşağı")); }

            if (hasPattern(tf1, longSide)) { s += 6; reasons.add("  1m: " + side(longSide, "🟢 boğa", "🔴 ayı") + " mum formasyonu"); }
        }
        // 5m formasyon teyidi
        if (hasPattern(tf5, longSide)) { s += 4; reasons.add("  5m: " + side(longSide, "🟢 boğa", "🔴 ayı") + " mum formasyonu"); }

        // ================= 15m ÜST TREND FİLTRESİ (±20p) =================
        if (htfBias * sign > 0) { s += 18; reasons.add("  15m: üst trend " + side(longSide, "YUKARI 🟢 (rüzgar arkadan)", "AŞAĞI 🔴 (rüzgar arkadan)")); }
        else if (htfBias == 0) { s += 6; reasons.add("  15m: üst trend nötr (yatay)"); }
        else { s -= 22; reasons.add("  15m: ⚠️ üst trende KARŞI scalp — risk yüksek, skor düşürüldü"); }

        // ================= Squeeze çıkışı bonusu =================
        double bbWidth5 = i5.getOrDefault("BB_width", 1.0);
        double bbPos5 = i5.getOrDefault("BB_position", 0.5);
        if (bbWidth5 > 0 && bbWidth5 < 0.04) {
            if (longSide && bbPos5 > 0.55) { s += 6; reasons.add("  5m: Bollinger sıkışması + üst banda çıkış (patlama olası)"); }
            else if (!longSide && bbPos5 < 0.45) { s += 6; reasons.add("  5m: Bollinger sıkışması + alt banda kırılım (patlama olası)"); }
        }

        return s;
    }

    /** 15m EMA dizilimi + SuperTrend + ADX'ten +1/0/-1 üst trend yanlılığı. */
    private int trendBias(AnalysisResult.TimeframeAnalysis tf15, Map<String, Double> i15) {
        if (tf15 == null || i15.isEmpty()) return 0;
        double ema20 = i15.getOrDefault("EMA20", 0.0);
        double ema50 = i15.getOrDefault("EMA50", 0.0);
        double st = i15.getOrDefault("SUPERTREND_DIR", 0.0);
        double adx = i15.getOrDefault("ADX14", 0.0);
        if (ema20 <= 0 || ema50 <= 0) return 0;

        boolean up = ema20 > ema50 && st >= 0;
        boolean down = ema20 < ema50 && st <= 0;
        if (up && adx >= 18) return 1;
        if (down && adx >= 18) return -1;
        if (up) return 1;
        if (down) return -1;
        return 0;
    }

    private boolean hasPattern(AnalysisResult.TimeframeAnalysis tf, boolean bullish) {
        if (tf == null || tf.getPatterns() == null) return false;
        for (CandlePattern p : tf.getPatterns()) {
            boolean isBull = switch (p) {
                case HAMMER, BULLISH_ENGULFING, MORNING_STAR, THREE_WHITE_SOLDIERS, PIERCING_LINE, HARAMI_BULLISH -> true;
                default -> false;
            };
            boolean isBear = switch (p) {
                case SHOOTING_STAR, BEARISH_ENGULFING, EVENING_STAR, THREE_BLACK_CROWS, DARK_CLOUD_COVER, HARAMI_BEARISH -> true;
                default -> false;
            };
            if (bullish && isBull) return true;
            if (!bullish && isBear) return true;
        }
        return false;
    }

    /** Scalp kaldıracı: yüksek güven + düşük oynaklık → daha yüksek; tavan 10x (sorumlu sınır). */
    private int suggestLeverage(int confidence, double atrPct) {
        int lev;
        if (atrPct < 0.003) lev = 10;
        else if (atrPct < 0.005) lev = 7;
        else if (atrPct < 0.008) lev = 5;
        else lev = 3;
        if (confidence < 80) lev = Math.min(lev, 5);
        if (confidence < 75) lev = Math.min(lev, 3);
        return Math.max(2, lev);
    }

    private String side(boolean longSide, String longTxt, String shortTxt) {
        return longSide ? longTxt : shortTxt;
    }

    private double firstPositive(double a, double fallback) {
        return a > 0 ? a : fallback;
    }

    private ScalpSignal neutral(String symbol, double price) {
        ScalpSignal s = new ScalpSignal();
        s.setSymbol(symbol);
        s.setDirection("NEUTRAL");
        s.setPrice(price);
        s.setConfidence(0);
        s.setTimestamp(LocalDateTime.now());
        s.setReasons(new ArrayList<>());
        return s;
    }
}
