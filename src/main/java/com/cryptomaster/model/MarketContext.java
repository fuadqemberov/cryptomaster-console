package com.cryptomaster.model;

/** Tüm piyasaya ait global bağlam (her döngüde bir kez hesaplanır). */
public class MarketContext {
    private double fearGreed = 50;   // 0 aşırı korku - 100 aşırı açgözlülük
    private String fearGreedLabel = "Neutral";
    private int btcRegime = 0;       // +1 BTC boğa, -1 ayı, 0 nötr

    public double getFearGreed() { return fearGreed; }
    public void setFearGreed(double fearGreed) { this.fearGreed = fearGreed; }
    public String getFearGreedLabel() { return fearGreedLabel; }
    public void setFearGreedLabel(String fearGreedLabel) { this.fearGreedLabel = fearGreedLabel; }
    public int getBtcRegime() { return btcRegime; }
    public void setBtcRegime(int btcRegime) { this.btcRegime = btcRegime; }
}