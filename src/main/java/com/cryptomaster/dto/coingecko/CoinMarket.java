package com.cryptomaster.dto.coingecko;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CoinMarket {
    private String id;
    private String symbol;
    private String name;
    @JsonProperty("current_price")
    private double currentPrice;

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
}