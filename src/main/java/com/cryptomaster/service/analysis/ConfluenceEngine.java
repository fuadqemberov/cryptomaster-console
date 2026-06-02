package com.cryptomaster.service.analysis;

import com.cryptomaster.model.AnalysisResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConfluenceEngine {

    /**
     * Çoklu zaman dilimi ve indikatör uyumuna göre 0-100 arası skor döner.
     */
    public int calculateConfluence(AnalysisResult analysisResult) {
        Map<String, AnalysisResult.TimeframeAnalysis> analyses = analysisResult.getTimeframeAnalyses();
        if (analyses == null || analyses.isEmpty()) return 50;

        double bullishScore = 0;
        double bearishScore = 0;
        int count = 0;

        for (AnalysisResult.TimeframeAnalysis tf : analyses.values()) {
            if (tf.getIndicators() == null) continue;
            Map<String, Double> ind = tf.getIndicators();
            count++;

            // RSI > 55 → bullish, < 45 → bearish
            double rsi = ind.getOrDefault("RSI14", 50.0);
            if (rsi > 55) bullishScore += 1;
            else if (rsi < 45) bearishScore += 1;

            // MACD histogram > 0 → bullish
            double macdHist = ind.getOrDefault("MACD_hist", 0.0);
            if (macdHist > 0) bullishScore += 0.5;
            else if (macdHist < 0) bearishScore += 0.5;

            // Trend
            if ("UPTREND".equals(tf.getTrend())) bullishScore += 1;
            else if ("DOWNTREND".equals(tf.getTrend())) bearishScore += 1;

            // BB pozisyonu: 0.7 üstü aşırı alım, 0.3 altı aşırı satım
            double bbPos = ind.getOrDefault("BB_position", 0.5);
            if (bbPos > 0.7) bearishScore += 0.5;
            else if (bbPos < 0.3) bullishScore += 0.5;
        }

        if (count == 0) return 50;
        double total = bullishScore + bearishScore;
        if (total == 0) return 50;
        int confidence = (int) ((bullishScore / total) * 100);
        return Math.min(100, Math.max(0, confidence));
    }
}