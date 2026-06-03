package com.cryptomaster.model;

/** Bir sembol için futures/türev piyasası metrikleri. */
public class DerivativesData {
    private String symbol;
    private boolean valid;              // futures verisi alınabildi mi
    private double fundingRate;         // anlık funding (örn 0.0001 = %0.01)
    private double oiChangePct;         // son periyottaki Open Interest % değişimi
    private double globalLongShortRatio;// retail hesap long/short oranı (>1 = long ağırlık)
    private double topTraderLongShortRatio; // büyük hesap pozisyon long/short oranı (akıllı para)
    private double takerBuySellRatio;   // agresif alıcı/satıcı oranı (>1 = alım baskısı)
    private double liqLongUsd;          // pencerede zorla kapanan LONG (forced sell) hacmi $
    private double liqShortUsd;         // pencerede zorla kapanan SHORT (forced buy) hacmi $

    public DerivativesData() {}
    public DerivativesData(String symbol) { this.symbol = symbol; }

    public static DerivativesData invalid(String symbol) {
        DerivativesData d = new DerivativesData(symbol);
        d.valid = false;
        d.globalLongShortRatio = 1.0;
        d.topTraderLongShortRatio = 1.0;
        d.takerBuySellRatio = 1.0;
        return d;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public double getFundingRate() { return fundingRate; }
    public void setFundingRate(double fundingRate) { this.fundingRate = fundingRate; }
    public double getOiChangePct() { return oiChangePct; }
    public void setOiChangePct(double oiChangePct) { this.oiChangePct = oiChangePct; }
    public double getGlobalLongShortRatio() { return globalLongShortRatio; }
    public void setGlobalLongShortRatio(double v) { this.globalLongShortRatio = v; }
    public double getTopTraderLongShortRatio() { return topTraderLongShortRatio; }
    public void setTopTraderLongShortRatio(double v) { this.topTraderLongShortRatio = v; }
    public double getTakerBuySellRatio() { return takerBuySellRatio; }
    public void setTakerBuySellRatio(double v) { this.takerBuySellRatio = v; }
    public double getLiqLongUsd() { return liqLongUsd; }
    public void setLiqLongUsd(double v) { this.liqLongUsd = v; }
    public double getLiqShortUsd() { return liqShortUsd; }
    public void setLiqShortUsd(double v) { this.liqShortUsd = v; }
}