package com.cryptomaster.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kısa vadeli (5-10 dakika) scalping sinyali.
 * Ana {@link TradingSignal} swing/pozisyon içindir; bu model ise hızlı,
 * dar stop'lu, yüksek frekanslı al-sat fırsatları için kullanılır.
 */
public class ScalpSignal {
    private String symbol;
    private String direction;        // LONG, SHORT, NEUTRAL
    private double price;
    private double entry;
    private double stopLoss;
    private double tp1;
    private double tp2;
    private int confidence;          // 0-100 (kendi yönündeki kanaat gücü)
    private int leverage;            // önerilen kaldıraç
    private double rrRatio;          // TP1 risk/ödül oranı
    private double atrPct;           // 5m ATR / fiyat (oynaklık)
    private String holdEstimate;     // tahmini pozisyon tutma süresi
    private List<String> reasons;
    private LocalDateTime timestamp;

    public ScalpSignal() {}

    public ScalpSignal(String symbol, String direction, double price, double entry,
                       double stopLoss, double tp1, double tp2, int confidence, int leverage,
                       double rrRatio, double atrPct, String holdEstimate,
                       List<String> reasons, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.direction = direction;
        this.price = price;
        this.entry = entry;
        this.stopLoss = stopLoss;
        this.tp1 = tp1;
        this.tp2 = tp2;
        this.confidence = confidence;
        this.leverage = leverage;
        this.rrRatio = rrRatio;
        this.atrPct = atrPct;
        this.holdEstimate = holdEstimate;
        this.reasons = reasons;
        this.timestamp = timestamp;
    }

    public boolean isActionable() {
        return "LONG".equals(direction) || "SHORT".equals(direction);
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

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

    public double getRrRatio() { return rrRatio; }
    public void setRrRatio(double rrRatio) { this.rrRatio = rrRatio; }

    public double getAtrPct() { return atrPct; }
    public void setAtrPct(double atrPct) { this.atrPct = atrPct; }

    public String getHoldEstimate() { return holdEstimate; }
    public void setHoldEstimate(String holdEstimate) { this.holdEstimate = holdEstimate; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
