package com.cryptomaster.service.report;

import com.cryptomaster.model.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ConsoleReportService {
    private static final Logger log = LoggerFactory.getLogger(ConsoleReportService.class);

    public void printDetailedReport(TradingSignal signal) {
        String directionColor = switch (signal.getDirection()) {
            case "LONG" -> "\033[1;32m";  // yeşil
            case "SHORT" -> "\033[1;31m"; // kırmızı
            default -> "\033[1;33m";       // sarı
        };
        String reset = "\033[0m";

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  ★ %s ANALİZ RAPORU ★%n", signal.getSymbol()));
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format(Locale.US, "║  Fiyat:     %.4f%n", signal.getCurrentPrice()));
        sb.append(String.format("║  Yön:       %s%s%s%n", directionColor, signal.getDirection(), reset));
        sb.append(String.format("║  Güven:     %% %d%n", signal.getConfidence()));
        sb.append(String.format("║  Kaldıraç:  %dx%n", signal.getLeverage()));
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format(Locale.US, "║  Giriş:     %.4f%n", signal.getEntry()));
        sb.append(String.format(Locale.US, "║  Stop Loss: %.4f%n", signal.getStopLoss()));
        sb.append(String.format(Locale.US, "║  TP1:       %.4f%n", signal.getTp1()));
        sb.append(String.format(Locale.US, "║  TP2:       %.4f%n", signal.getTp2()));
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append("║  İNDİKATÖR & MUM & TREND ANALİZİ:\n");

        if (signal.getReasons() != null) {
            for (String reason : signal.getReasons()) {
                sb.append("║  • ").append(reason).append("\n");
            }
        }

        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  ÖZET: %s%n", signal.getSummary()));
        sb.append("╚══════════════════════════════════════════════════╝\n");

        log.info(sb.toString());
    }

    public void printAllCoinsReport(List<TradingSignal> signals) {
        StringBuilder sb = new StringBuilder("\n┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│                ★ TÜM COİN ÖZET TABLOSU ★                    │\n");
        sb.append("├──────────┬──────────┬────────────┬────────┬────────┬────────┤\n");
        sb.append(String.format("│ %-8s │ %-8s │ %-10s │ %-6s │ %-6s │ %-6s │%n",
                "Coin", "Yön", "Fiyat", "Güven", "Kaldıraç", "Sinyal"));
        sb.append("├──────────┼──────────┼────────────┼────────┼────────┼────────┤\n");

        for (TradingSignal s : signals) {
            String icon = switch (s.getDirection()) {
                case "LONG" -> "🟢";
                case "SHORT" -> "🔴";
                default -> "🟡";
            };
            sb.append(String.format(Locale.US, "│ %-8s │ %s %-6s │ %10.4f │  %% %-3d │  %-4dx  │ %-6s │%n",
                    s.getSymbol(), icon, s.getDirection(), s.getCurrentPrice(),
                    s.getConfidence(), s.getLeverage(),
                    s.getConfidence() >= 65 ? "AL" : (s.getConfidence() <= 35 ? "SAT" : "BEKLE")));
        }
        sb.append("└──────────┴──────────┴────────────┴────────┴────────┴────────┘\n");
        log.info(sb.toString());
    }
}