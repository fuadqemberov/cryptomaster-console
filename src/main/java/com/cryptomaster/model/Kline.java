package com.cryptomaster.model;

import java.time.Instant;

public class Kline {
    private long openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private long closeTime;
    private String interval; // 1m, 5m, 1h, etc.

    public Kline() {}

    public Kline(long openTime, double open, double high, double low, double close, double volume) {
        this.openTime = openTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Getter & Setter'lar (hepsi)
    public long getOpenTime() { return openTime; }
    public void setOpenTime(long openTime) { this.openTime = openTime; }

    public Instant getInstant() { return Instant.ofEpochMilli(openTime); }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }

    public long getCloseTime() { return closeTime; }
    public void setCloseTime(long closeTime) { this.closeTime = closeTime; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
}