package com.cryptomaster.model;

import java.time.LocalDateTime;
import java.util.List;

public class TradingSignal {
    private String symbol;
    private String direction;     // LONG, SHORT, NEUTRAL
    private double currentPrice;
    private double entry;
    private double stopLoss;
    private double tp1;
    private double tp2;
    private int confidence;       // 0–100
    private int leverage;         // önerilen kaldıraç (1x, 2x, 3x, 5x, 10x)
    private String summary;
    private List<String> reasons;
    private LocalDateTime timestamp;

    public TradingSignal() {}

    public TradingSignal(String symbol, String direction, double currentPrice, double entry,
                         double stopLoss, double tp1, double tp2, int confidence, int leverage,
                         String summary, List<String> reasons, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.direction = direction;
        this.currentPrice = currentPrice;
        this.entry = entry;
        this.stopLoss = stopLoss;
        this.tp1 = tp1;
        this.tp2 = tp2;
        this.confidence = confidence;
        this.leverage = leverage;
        this.summary = summary;
        this.reasons = reasons;
        this.timestamp = timestamp;
    }

    // Getter ve Setter'lar (hepsi)
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getEntry() { return entry; }
    public void setEntry(double entry) { this.entry = entry; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTp1() { return tp1; }
    public void setTp1(double tp1) { this.tp1 = tp1; }

    public double getTp2() { return tp2; }
    public void setTp2(double tp2) { this.tp2 = tp2; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public int getLeverage() { return leverage; }
    public void setLeverage(int leverage) { this.leverage = leverage; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}