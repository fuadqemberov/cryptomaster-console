package com.cryptomaster.service.analysis;

import com.cryptomaster.model.DerivativesData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Türev verilerini yönlü baskıya çevirir.
 * Çıktı: 0-100 (50 nötr, >50 boğa baskısı). Ayrıca insan-okur gerekçeler.
 */
@Component
public class DerivativesAnalyzer {

    public static class Result {
        public final int score;            // 0-100
        public final List<String> reasons;
        public Result(int score, List<String> reasons) { this.score = score; this.reasons = reasons; }
    }

    public Result analyze(DerivativesData d) {
        List<String> reasons = new ArrayList<>();
        if (d == null || !d.isValid()) {
            reasons.add("  💹 TÜREV: veri yok (spot-only sembol olabilir)");
            return new Result(50, reasons);
        }

        double bull = 0, bear = 0;
        reasons.add("  💹 TÜREV PİYASA:");

        // 1) Funding rate — aşırı uçlar KONTRARYAN
        double fr = d.getFundingRate();
        double frPct = fr * 100;
        if (fr <= -0.0005) { bull += 1.5; reasons.add(String.format("     • Funding %.4f%% → çok negatif: shortlar kalabalık, SIKIŞMA→LONG", frPct)); }
        else if (fr < -0.0001) { bull += 0.5; reasons.add(String.format("     • Funding %.4f%% → negatif: hafif boğa", frPct)); }
        else if (fr >= 0.0008) { bear += 1.5; reasons.add(String.format("     • Funding %.4f%% → çok pozitif: longlar kalabalık, SIKIŞMA→SHORT", frPct)); }
        else if (fr > 0.0003) { bear += 0.5; reasons.add(String.format("     • Funding %.4f%% → pozitif: hafif ayı", frPct)); }
        else reasons.add(String.format("     • Funding %.4f%% → nötr", frPct));

        // 2) Open Interest değişimi (taker yönüyle birlikte)
        double oi = d.getOiChangePct();
        double taker = d.getTakerBuySellRatio();
        if (oi > 2 && taker > 1.05) { bull += 1.0; reasons.add(String.format("     • OI +%.1f%% & alım baskısı → yeni LONG akışı", oi)); }
        else if (oi > 2 && taker < 0.95) { bear += 1.0; reasons.add(String.format("     • OI +%.1f%% & satım baskısı → yeni SHORT akışı", oi)); }
        else if (oi < -2) { reasons.add(String.format("     • OI %.1f%% → pozisyon kapanışı (trend zayıflıyor)", oi)); }
        else reasons.add(String.format("     • OI %.1f%% → belirgin akış yok", oi));

        // 3) Taker alım/satım
        if (taker > 1.1) { bull += 0.75; reasons.add(String.format("     • Taker B/S %.2f → agresif ALIM", taker)); }
        else if (taker < 0.9) { bear += 0.75; reasons.add(String.format("     • Taker B/S %.2f → agresif SATIM", taker)); }

        // 4) Retail long/short — KONTRARYAN
        double gls = d.getGlobalLongShortRatio();
        if (gls >= 2.5) { bear += 1.0; reasons.add(String.format("     • Retail L/S %.2f → kalabalık LONG (kontraryan SHORT)", gls)); }
        else if (gls <= 0.6) { bull += 1.0; reasons.add(String.format("     • Retail L/S %.2f → kalabalık SHORT (kontraryan LONG)", gls)); }

        // 5) Büyük hesap (akıllı para) — TAKİP ET
        double tls = d.getTopTraderLongShortRatio();
        if (tls >= 1.3) { bull += 1.25; reasons.add(String.format("     • Büyük hesap L/S %.2f → akıllı para LONG", tls)); }
        else if (tls <= 0.77) { bear += 1.25; reasons.add(String.format("     • Büyük hesap L/S %.2f → akıllı para SHORT", tls)); }

        // 6) Likidasyonlar — kademeli kapanış sonrası KONTRARYAN tepki
        double liqLong = d.getLiqLongUsd();
        double liqShort = d.getLiqShortUsd();
        double totalLiq = liqLong + liqShort;
        if (totalLiq > 50_000) {
            if (liqLong > liqShort * 2) { bull += 1.0; reasons.add(String.format("     • LONG likidasyonu $%.0fK → kapitülasyon, tepki LONG olası", liqLong / 1000)); }
            else if (liqShort > liqLong * 2) { bear += 1.0; reasons.add(String.format("     • SHORT likidasyonu $%.0fK → zirve, tepki SHORT olası", liqShort / 1000)); }
        }

        double total = bull + bear;
        int score = total == 0 ? 50 : (int) Math.round((bull / total) * 100);
        reasons.add(String.format("     ➤ Türev baskısı: %d/100 (%s)", score,
                score > 60 ? "BOĞA" : score < 40 ? "AYI" : "nötr"));
        return new Result(score, reasons);
    }
}