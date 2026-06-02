package com.cryptomaster.service.report;

import com.cryptomaster.model.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsoleReportService {
    private static final Logger log = LoggerFactory.getLogger(ConsoleReportService.class);

    public void printDetailedReport(TradingSignal signal) {
        String header = String.format("★ %s ANALİZ RAPORU ★", signal.getSymbol());
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════\n");
        sb.append(header).append("\n");
        sb.append("Fiyat: ").append(String.format("%.2f", signal.getCurrentPrice())).append("\n");
        sb.append("Yön:   ").append(signal.getDirection()).append("\n");
        sb.append("Güven: %").append(signal.getConfidence()).append("\n");
        sb.append("───────────────────────────────────\n");
        sb.append("Giriş:     ").append(String.format("%.2f", signal.getEntry())).append("\n");
        sb.append("Stop Loss: ").append(String.format("%.2f", signal.getStopLoss())).append("\n");
        sb.append("TP1:       ").append(String.format("%.2f", signal.getTp1())).append("\n");
        sb.append("TP2:       ").append(String.format("%.2f", signal.getTp2())).append("\n");
        sb.append("───────────────────────────────────\n");
        sb.append("Sebepler:\n");
        signal.getReasons().forEach(r -> sb.append("  • ").append(r).append("\n"));
        sb.append("Özet: ").append(signal.getSummary()).append("\n");
        sb.append("═══════════════════════════════════\n");
        log.info(sb.toString());
    }

    public void printAllCoinsReport(List<TradingSignal> signals) {
        StringBuilder sb = new StringBuilder("\n***** TÜM COIN ÖZETİ *****\n");
        sb.append(String.format("%-12s %-10s %-12s %-10s%n", "Coin", "Yön", "Fiyat", "Güven"));
        sb.append("--------------------------------------------\n");
        for (TradingSignal s : signals) {
            sb.append(String.format("%-12s %-10s %-12.2f %-10d%n", s.getSymbol(), s.getDirection(), s.getCurrentPrice(), s.getConfidence()));
        }
        sb.append("--------------------------------------------\n");
        log.info(sb.toString());
    }
}