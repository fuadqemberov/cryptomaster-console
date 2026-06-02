package com.cryptomaster.service.strategy;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.TradingSignal;
import com.cryptomaster.service.analysis.ConfluenceEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SignalGenerationService {
    @Autowired
    private ConfluenceEngine confluenceEngine;

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

        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, AnalysisResult.TimeframeAnalysis> e : analysisResult.getTimeframeAnalyses().entrySet()) {
            String interval = e.getKey();
            AnalysisResult.TimeframeAnalysis tf = e.getValue();
            reasons.add(String.format("[%s] Trend: %s (%.1f) | Formasyon: %s", interval, tf.getTrend(), tf.getTrendStrength(), tf.getPatterns()));
        }

        return new TradingSignal(
                symbol, direction, currentPrice, entry, sl, tp1, tp2,
                confidence,
                String.format("%s sinyali (güven: %d%%)", direction, confidence),
                reasons,
                LocalDateTime.now()
        );
    }

    private double getAtrFromAnalysis(AnalysisResult analysisResult) {
        AnalysisResult.TimeframeAnalysis tf = analysisResult.getTimeframeAnalyses().get("1h");
        if (tf != null && tf.getIndicators() != null && tf.getIndicators().containsKey("ATR14")) {
            return tf.getIndicators().get("ATR14");
        }
        // fallback
        return 50;
    }
}