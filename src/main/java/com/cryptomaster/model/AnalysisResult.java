package com.cryptomaster.model;

import java.util.Map;

public class AnalysisResult {
    private Map<String, TimeframeAnalysis> timeframeAnalyses; // interval -> analiz
    private int confluenceScore;

    public AnalysisResult() {}

    public Map<String, TimeframeAnalysis> getTimeframeAnalyses() { return timeframeAnalyses; }
    public void setTimeframeAnalyses(Map<String, TimeframeAnalysis> timeframeAnalyses) { this.timeframeAnalyses = timeframeAnalyses; }

    public int getConfluenceScore() { return confluenceScore; }
    public void setConfluenceScore(int confluenceScore) { this.confluenceScore = confluenceScore; }

    public static class TimeframeAnalysis {
        private Map<String, Double> indicators;  // RSI, MACD_hist, EMA50, vs.
        private java.util.List<CandlePattern> patterns;
        private String trend;   // UPTREND, DOWNTREND, SIDEWAYS
        private double trendStrength;

        public TimeframeAnalysis() {}

        public Map<String, Double> getIndicators() { return indicators; }
        public void setIndicators(Map<String, Double> indicators) { this.indicators = indicators; }

        public java.util.List<CandlePattern> getPatterns() { return patterns; }
        public void setPatterns(java.util.List<CandlePattern> patterns) { this.patterns = patterns; }

        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }

        public double getTrendStrength() { return trendStrength; }
        public void setTrendStrength(double trendStrength) { this.trendStrength = trendStrength; }
    }
}