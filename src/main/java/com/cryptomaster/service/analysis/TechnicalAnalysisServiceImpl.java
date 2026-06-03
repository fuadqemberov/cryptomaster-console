package com.cryptomaster.service.analysis;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.CandlePattern;
import com.cryptomaster.model.Kline;
import com.cryptomaster.util.IndicatorUtils;
import com.cryptomaster.util.KlineConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;

import java.util.*;

@Service
public class TechnicalAnalysisServiceImpl implements TechnicalAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisServiceImpl.class);

    @Override
    public AnalysisResult analyze(String symbol, Map<String, List<Kline>> klinesByInterval) {
        Map<String, AnalysisResult.TimeframeAnalysis> analyses = new LinkedHashMap<>();

        for (Map.Entry<String, List<Kline>> entry : klinesByInterval.entrySet()) {
            String interval = entry.getKey();
            List<Kline> klines = entry.getValue();
            if (klines == null || klines.size() < 30) continue; // çok kısa seriyi atla

            BarSeries series = KlineConverter.convertToBarSeries(klines);
            AnalysisResult.TimeframeAnalysis tf = new AnalysisResult.TimeframeAnalysis();
            tf.setIndicators(calculateIndicators(series));
            tf.setPatterns(detectCandlePatterns(series));
            String[] trend = detectTrend(series, tf.getIndicators());
            tf.setTrend(trend[0]);
            tf.setTrendStrength(Double.parseDouble(trend[1]));
            analyses.put(interval, tf);
        }

        AnalysisResult result = new AnalysisResult();
        result.setTimeframeAnalyses(analyses);
        return result;
    }

    private Map<String, Double> calculateIndicators(BarSeries series) {
        Map<String, Double> ind = new LinkedHashMap<>();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int end = series.getEndIndex();
        int count = series.getBarCount();
        double currentClose = IndicatorUtils.getLastValue(close);

        // ---- Momentum ----
        RSIIndicator rsi = new RSIIndicator(close, 14);
        ind.put("RSI14", IndicatorUtils.getLastValue(rsi));

        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        ind.put("MACD_hist", IndicatorUtils.getLastValue(macd) - IndicatorUtils.getLastValue(macdSignal));

        // Stochastic RSI (RSI üzerinden 14 periyot normalize) → 0-100
        HighestValueIndicator rsiHigh = new HighestValueIndicator(rsi, 14);
        LowestValueIndicator rsiLow = new LowestValueIndicator(rsi, 14);
        double rsiNow = IndicatorUtils.getLastValue(rsi);
        double rsiHi = IndicatorUtils.getLastValue(rsiHigh);
        double rsiLo = IndicatorUtils.getLastValue(rsiLow);
        double stochRsi = (rsiHi - rsiLo) == 0 ? 50.0 : ((rsiNow - rsiLo) / (rsiHi - rsiLo)) * 100.0;
        ind.put("STOCH_RSI", stochRsi);

        // ---- Trend / EMA'lar ----
        ind.put("EMA20", IndicatorUtils.getLastValue(new EMAIndicator(close, 20)));
        ind.put("EMA50", IndicatorUtils.getLastValue(new EMAIndicator(close, 50)));
        ind.put("EMA200", IndicatorUtils.getLastValue(new EMAIndicator(close, 200)));

        // ---- ADX + Yön gücü (DI) ----
        ind.put("ADX14", IndicatorUtils.getLastValue(new ADXIndicator(series, 14)));
        ind.put("PLUS_DI", IndicatorUtils.getLastValue(new PlusDIIndicator(series, 14)));
        ind.put("MINUS_DI", IndicatorUtils.getLastValue(new MinusDIIndicator(series, 14)));

        // ---- Volatilite ----
        ATRIndicator atr = new ATRIndicator(series, 14);
        ind.put("ATR14", IndicatorUtils.getLastValue(atr));

        // Bollinger %B + bant genişliği (squeeze tespiti için)
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(close, 20);
        double mid = IndicatorUtils.getLastValue(sma20);
        double dev = IndicatorUtils.getLastValue(sd);
        double bbUpper = mid + 2 * dev;
        double bbLower = mid - 2 * dev;
        double bbPosition = (bbUpper - bbLower) == 0 ? 0.5 : (currentClose - bbLower) / (bbUpper - bbLower);
        ind.put("BB_position", bbPosition);
        ind.put("BB_width", mid == 0 ? 0.0 : (bbUpper - bbLower) / mid); // normalize genişlik

        // ---- Hacim ----
        VolumeIndicator vol = new VolumeIndicator(series);
        SMAIndicator volSma = new SMAIndicator(vol, 20);
        double lastVol = IndicatorUtils.getLastValue(vol);
        double avgVol = IndicatorUtils.getLastValue(volSma);
        ind.put("RVOL", avgVol == 0 ? 1.0 : lastVol / avgVol); // göreli hacim

        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);
        double obvNow = IndicatorUtils.getLastValue(obv);
        double obvPrev = end >= 3 ? obv.getValue(end - 3).doubleValue() : obvNow;
        ind.put("OBV_SLOPE", Math.signum(obvNow - obvPrev)); // +1 birikim / -1 dağıtım

        int vwapBars = Math.min(50, Math.max(2, count));
        VWAPIndicator vwap = new VWAPIndicator(series, vwapBars);
        double vwapVal = IndicatorUtils.getLastValue(vwap);
        ind.put("VWAP", vwapVal);
        ind.put("VWAP_REL", vwapVal == 0 ? 0.0 : Math.signum(currentClose - vwapVal)); // fiyat VWAP üstü/altı

        // ---- SuperTrend yönü ----
        ind.put("SUPERTREND_DIR", (double) superTrendDirection(series, 10, 3.0));

        // ---- Divergence (RSI / fiyat) ----
        ind.put("DIVERGENCE", (double) detectDivergence(series, rsi, 34));

        // ---- Destek / Direnç (son 50 mum swing) ----
        int srLookback = Math.min(50, count);
        double support = currentClose, resistance = currentClose;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = end - srLookback + 1; i <= end; i++) {
            if (i < series.getBeginIndex()) continue;
            Bar b = series.getBar(i);
            lo = Math.min(lo, b.getLowPrice().doubleValue());
            hi = Math.max(hi, b.getHighPrice().doubleValue());
        }
        if (lo != Double.MAX_VALUE) support = lo;
        if (hi != -Double.MAX_VALUE) resistance = hi;
        ind.put("SUPPORT", support);
        ind.put("RESISTANCE", resistance);

        return ind;
    }

    /** ATR tabanlı SuperTrend; +1 (yükseliş) veya -1 (düşüş) döner. */
    private int superTrendDirection(BarSeries series, int period, double mult) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        if (end - begin < period + 1) return 0;

        ATRIndicator atr = new ATRIndicator(series, period);
        double prevFinalUpper = 0, prevFinalLower = 0;
        int dir = 1;

        for (int i = begin; i <= end; i++) {
            Bar bar = series.getBar(i);
            double high = bar.getHighPrice().doubleValue();
            double low = bar.getLowPrice().doubleValue();
            double close = bar.getClosePrice().doubleValue();
            double hl2 = (high + low) / 2.0;
            double a = atr.getValue(i).doubleValue();
            double basicUpper = hl2 + mult * a;
            double basicLower = hl2 - mult * a;

            double finalUpper, finalLower;
            if (i == begin) {
                finalUpper = basicUpper;
                finalLower = basicLower;
            } else {
                double prevClose = series.getBar(i - 1).getClosePrice().doubleValue();
                finalUpper = (basicUpper < prevFinalUpper || prevClose > prevFinalUpper) ? basicUpper : prevFinalUpper;
                finalLower = (basicLower > prevFinalLower || prevClose < prevFinalLower) ? basicLower : prevFinalLower;
                if (close > prevFinalUpper) dir = 1;
                else if (close < prevFinalLower) dir = -1;
            }
            prevFinalUpper = finalUpper;
            prevFinalLower = finalLower;
        }
        return dir;
    }

    /** RSI ile fiyat arasında uyumsuzluk: +1 boğa divergence, -1 ayı divergence, 0 yok. */
    private int detectDivergence(BarSeries series, RSIIndicator rsi, int lookback) {
        int end = series.getEndIndex();
        int begin = series.getBeginIndex();
        if (end - begin < lookback) return 0;

        int half = lookback / 2;
        int oldStart = end - lookback + 1;
        int midPoint = end - half;

        // Eski yarı [oldStart, midPoint], yeni yarı [midPoint+1, end]
        double oldLow = Double.MAX_VALUE, newLow = Double.MAX_VALUE;
        double oldHigh = -Double.MAX_VALUE, newHigh = -Double.MAX_VALUE;
        int oldLowIdx = oldStart, newLowIdx = midPoint + 1, oldHighIdx = oldStart, newHighIdx = midPoint + 1;

        for (int i = oldStart; i <= midPoint; i++) {
            double c = series.getBar(i).getClosePrice().doubleValue();
            if (c < oldLow) { oldLow = c; oldLowIdx = i; }
            if (c > oldHigh) { oldHigh = c; oldHighIdx = i; }
        }
        for (int i = midPoint + 1; i <= end; i++) {
            double c = series.getBar(i).getClosePrice().doubleValue();
            if (c < newLow) { newLow = c; newLowIdx = i; }
            if (c > newHigh) { newHigh = c; newHighIdx = i; }
        }

        double rsiOldLow = rsi.getValue(oldLowIdx).doubleValue();
        double rsiNewLow = rsi.getValue(newLowIdx).doubleValue();
        double rsiOldHigh = rsi.getValue(oldHighIdx).doubleValue();
        double rsiNewHigh = rsi.getValue(newHighIdx).doubleValue();

        // Boğa: fiyat daha düşük dip, RSI daha yüksek dip
        if (newLow < oldLow && rsiNewLow > rsiOldLow + 1) return 1;
        // Ayı: fiyat daha yüksek tepe, RSI daha düşük tepe
        if (newHigh > oldHigh && rsiNewHigh < rsiOldHigh - 1) return -1;
        return 0;
    }

    private List<CandlePattern> detectCandlePatterns(BarSeries series) {
        List<CandlePattern> patterns = new ArrayList<>();
        int last = series.getEndIndex();
        if (last < 3) return patterns;

        Bar prev = series.getBar(last - 1);
        Bar curr = series.getBar(last);

        if (isDoji(curr)) patterns.add(CandlePattern.DOJI);

        // DÜZELTİLDİ: gölge uzunlukları doğru hesaplanıyor
        double open = curr.getOpenPrice().doubleValue();
        double cls = curr.getClosePrice().doubleValue();
        double high = curr.getHighPrice().doubleValue();
        double low = curr.getLowPrice().doubleValue();
        double body = Math.abs(cls - open);
        double bodyTop = Math.max(open, cls);
        double bodyBottom = Math.min(open, cls);
        double lowerShadow = bodyBottom - low;
        double upperShadow = high - bodyTop;
        if (body > 0) {
            if (lowerShadow > body * 2 && upperShadow < body * 0.5) patterns.add(CandlePattern.HAMMER);
            if (upperShadow > body * 2 && lowerShadow < body * 0.5) patterns.add(CandlePattern.SHOOTING_STAR);
        }

        if (isBullishEngulfing(prev, curr)) patterns.add(CandlePattern.BULLISH_ENGULFING);
        if (isBearishEngulfing(prev, curr)) patterns.add(CandlePattern.BEARISH_ENGULFING);

        if (last >= 3) {
            Bar first = series.getBar(last - 2);
            if (isMorningStar(first, prev, curr)) patterns.add(CandlePattern.MORNING_STAR);
            if (isEveningStar(first, prev, curr)) patterns.add(CandlePattern.EVENING_STAR);
        }
        if (last >= 4) {
            Bar b1 = series.getBar(last - 2);
            Bar b2 = series.getBar(last - 1);
            Bar b3 = series.getBar(last);
            if (isThreeWhiteSoldiers(b1, b2, b3)) patterns.add(CandlePattern.THREE_WHITE_SOLDIERS);
            if (isThreeBlackCrows(b1, b2, b3)) patterns.add(CandlePattern.THREE_BLACK_CROWS);
        }
        if (isPiercingLine(prev, curr)) patterns.add(CandlePattern.PIERCING_LINE);
        if (isDarkCloudCover(prev, curr)) patterns.add(CandlePattern.DARK_CLOUD_COVER);
        if (isHaramiBullish(prev, curr)) patterns.add(CandlePattern.HARAMI_BULLISH);
        if (isHaramiBearish(prev, curr)) patterns.add(CandlePattern.HARAMI_BEARISH);

        return patterns;
    }

    private boolean isDoji(Bar bar) {
        double range = bar.getHighPrice().doubleValue() - bar.getLowPrice().doubleValue();
        if (range == 0) return false;
        return Math.abs(bar.getClosePrice().doubleValue() - bar.getOpenPrice().doubleValue()) / range < 0.1;
    }

    private boolean isBullishEngulfing(Bar prev, Bar curr) {
        return prev.getClosePrice().isLessThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isGreaterThan(curr.getOpenPrice()) &&
               curr.getClosePrice().isGreaterThan(prev.getOpenPrice()) &&
               curr.getOpenPrice().isLessThan(prev.getClosePrice());
    }

    private boolean isBearishEngulfing(Bar prev, Bar curr) {
        return prev.getClosePrice().isGreaterThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isLessThan(curr.getOpenPrice()) &&
               curr.getOpenPrice().isGreaterThan(prev.getClosePrice()) &&
               curr.getClosePrice().isLessThan(prev.getOpenPrice());
    }

    private boolean isMorningStar(Bar first, Bar second, Bar third) {
        return first.getClosePrice().isLessThan(first.getOpenPrice()) &&
               Math.abs(second.getClosePrice().doubleValue() - second.getOpenPrice().doubleValue()) <
               (first.getOpenPrice().doubleValue() - first.getClosePrice().doubleValue()) * 0.3 &&
               third.getClosePrice().isGreaterThan(third.getOpenPrice()) &&
               third.getClosePrice().isGreaterThan(first.getOpenPrice());
    }

    private boolean isEveningStar(Bar first, Bar second, Bar third) {
        return first.getClosePrice().isGreaterThan(first.getOpenPrice()) &&
               Math.abs(second.getClosePrice().doubleValue() - second.getOpenPrice().doubleValue()) <
               (first.getClosePrice().doubleValue() - first.getOpenPrice().doubleValue()) * 0.3 &&
               third.getClosePrice().isLessThan(third.getOpenPrice()) &&
               third.getClosePrice().isLessThan(first.getOpenPrice());
    }

    private boolean isThreeWhiteSoldiers(Bar b1, Bar b2, Bar b3) {
        return b1.getClosePrice().isGreaterThan(b1.getOpenPrice()) &&
               b2.getClosePrice().isGreaterThan(b2.getOpenPrice()) &&
               b3.getClosePrice().isGreaterThan(b3.getOpenPrice()) &&
               b2.getClosePrice().isGreaterThan(b1.getClosePrice()) &&
               b3.getClosePrice().isGreaterThan(b2.getClosePrice());
    }

    private boolean isThreeBlackCrows(Bar b1, Bar b2, Bar b3) {
        return b1.getClosePrice().isLessThan(b1.getOpenPrice()) &&
               b2.getClosePrice().isLessThan(b2.getOpenPrice()) &&
               b3.getClosePrice().isLessThan(b3.getOpenPrice()) &&
               b2.getClosePrice().isLessThan(b1.getClosePrice()) &&
               b3.getClosePrice().isLessThan(b2.getClosePrice());
    }

    private boolean isPiercingLine(Bar prev, Bar curr) {
        return prev.getClosePrice().isLessThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isGreaterThan(curr.getOpenPrice()) &&
               curr.getClosePrice().isGreaterThan(prev.getClosePrice()) &&
               curr.getClosePrice().isLessThan(prev.getOpenPrice());
    }

    private boolean isDarkCloudCover(Bar prev, Bar curr) {
        return prev.getClosePrice().isGreaterThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isLessThan(curr.getOpenPrice()) &&
               curr.getOpenPrice().isGreaterThan(prev.getHighPrice()) &&
               curr.getClosePrice().isGreaterThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isLessThan(prev.getClosePrice());
    }

    private boolean isHaramiBullish(Bar prev, Bar curr) {
        return prev.getClosePrice().isLessThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isGreaterThan(curr.getOpenPrice()) &&
               curr.getHighPrice().isLessThan(prev.getOpenPrice()) &&
               curr.getLowPrice().isGreaterThan(prev.getClosePrice());
    }

    private boolean isHaramiBearish(Bar prev, Bar curr) {
        return prev.getClosePrice().isGreaterThan(prev.getOpenPrice()) &&
               curr.getClosePrice().isLessThan(curr.getOpenPrice()) &&
               curr.getHighPrice().isLessThan(prev.getClosePrice()) &&
               curr.getLowPrice().isGreaterThan(prev.getOpenPrice());
    }

    /** EMA20/50/200 dizilimi + ADX gücü ile trend tespiti. */
    private String[] detectTrend(BarSeries series, Map<String, Double> ind) {
        double lastClose = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        double e20 = ind.getOrDefault("EMA20", lastClose);
        double e50 = ind.getOrDefault("EMA50", lastClose);
        double e200 = ind.getOrDefault("EMA200", lastClose);
        double adx = ind.getOrDefault("ADX14", 0.0);
        int stDir = (int) Math.signum(ind.getOrDefault("SUPERTREND_DIR", 0.0));

        // ADX'i 0-100 güç skoruna çevir (25 ADX ~ güçlü)
        double strength = Math.min(100.0, adx * 2.5);

        boolean bullStack = e20 > e50 && e50 > e200 && lastClose > e20;
        boolean bearStack = e20 < e50 && e50 < e200 && lastClose < e20;

        if (bullStack && stDir >= 0 && adx >= 20) {
            return new String[]{"UPTREND", String.valueOf(Math.max(60, (int) strength))};
        } else if (bearStack && stDir <= 0 && adx >= 20) {
            return new String[]{"DOWNTREND", String.valueOf(Math.max(60, (int) strength))};
        } else if (e20 > e50 && lastClose > e50) {
            return new String[]{"UPTREND", String.valueOf((int) Math.max(40, strength))};
        } else if (e20 < e50 && lastClose < e50) {
            return new String[]{"DOWNTREND", String.valueOf((int) Math.max(40, strength))};
        }
        return new String[]{"SIDEWAYS", String.valueOf((int) Math.min(35, strength))};
    }
}