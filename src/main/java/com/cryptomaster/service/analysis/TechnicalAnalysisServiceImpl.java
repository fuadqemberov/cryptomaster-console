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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

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
            if (klines == null || klines.isEmpty()) continue;

            BarSeries series = KlineConverter.convertToBarSeries(klines);
            AnalysisResult.TimeframeAnalysis tf = new AnalysisResult.TimeframeAnalysis();
            tf.setIndicators(calculateIndicators(series));
            tf.setPatterns(detectCandlePatterns(series));
            String[] trend = detectTrend(series);
            tf.setTrend(trend[0]);
            tf.setTrendStrength(Double.parseDouble(trend[1]));
            analyses.put(interval, tf);
        }

        AnalysisResult result = new AnalysisResult();
        result.setTimeframeAnalyses(analyses);
        // ConfluenceEngine ayrıca hesaplanacak, burada set etmiyoruz.
        return result;
    }

    private Map<String, Double> calculateIndicators(BarSeries series) {
        Map<String, Double> ind = new LinkedHashMap<>();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        ind.put("RSI14", IndicatorUtils.getLastValue(new RSIIndicator(close, 14)));
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        ind.put("MACD_hist", IndicatorUtils.getLastValue(macd) - IndicatorUtils.getLastValue(macdSignal));
        ind.put("EMA20", IndicatorUtils.getLastValue(new EMAIndicator(close, 20)));
        ind.put("EMA50", IndicatorUtils.getLastValue(new EMAIndicator(close, 50)));

        // Bollinger genişliği
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(close, 20);
        double bbUpper = IndicatorUtils.getLastValue(sma20) + 2 * IndicatorUtils.getLastValue(sd);
        double bbLower = IndicatorUtils.getLastValue(sma20) - 2 * IndicatorUtils.getLastValue(sd);
        double currentClose = IndicatorUtils.getLastValue(close);
        double bbPosition = (currentClose - bbLower) / (bbUpper - bbLower); // 0-1 arası
        ind.put("BB_position", bbPosition);

        ATRIndicator atr = new ATRIndicator(series, 14);
        ind.put("ATR14", IndicatorUtils.getLastValue(atr));

        ADXIndicator adx = new ADXIndicator(series, 14);
        ind.put("ADX14", IndicatorUtils.getLastValue(adx));

        return ind;
    }

    private List<CandlePattern> detectCandlePatterns(BarSeries series) {
        List<CandlePattern> patterns = new ArrayList<>();
        int last = series.getEndIndex();
        if (last < 3) return patterns;

        Bar prev = series.getBar(last - 1);
        Bar curr = series.getBar(last);

        // Doji
        if (isDoji(curr)) patterns.add(CandlePattern.DOJI);

        double body = Math.abs(curr.getClosePrice().doubleValue() - curr.getOpenPrice().doubleValue());
        double lowerShadow = curr.getLowPrice().doubleValue();
        double upperShadow = curr.getHighPrice().doubleValue() - Math.max(curr.getOpenPrice().doubleValue(), curr.getClosePrice().doubleValue());
        if (lowerShadow > body * 2 && upperShadow < body * 0.3) patterns.add(CandlePattern.HAMMER);
        if (upperShadow > body * 2 && lowerShadow < body * 0.3) patterns.add(CandlePattern.SHOOTING_STAR);

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

    // Yardımcı formasyon metodları öncekiyle aynı, kısaltılmış hali:
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
               (first.getClosePrice().doubleValue() - first.getOpenPrice().doubleValue()) * 0.3 &&
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

    // Basit trend tespiti: EMA20/EMA50 + Higher High/Low
    private String[] detectTrend(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema20 = new EMAIndicator(close, 20);
        EMAIndicator ema50 = new EMAIndicator(close, 50);
        double lastClose = IndicatorUtils.getLastValue(close);
        double e20 = IndicatorUtils.getLastValue(ema20);
        double e50 = IndicatorUtils.getLastValue(ema50);

        int end = series.getEndIndex();
        boolean higherHigh = false;
        if (end >= 5) {
            double prevMax = series.getBar(end-1).getHighPrice().doubleValue();
            double currMax = series.getBar(end).getHighPrice().doubleValue();
            higherHigh = currMax > prevMax;
        }

        if (e20 > e50 && lastClose > e20 && higherHigh) {
            return new String[]{"UPTREND", "70"};
        } else if (e20 < e50 && lastClose < e20 && !higherHigh) {
            return new String[]{"DOWNTREND", "70"};
        }
        return new String[]{"SIDEWAYS", "30"};
    }
}