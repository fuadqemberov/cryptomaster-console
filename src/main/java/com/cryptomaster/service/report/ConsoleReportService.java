package com.cryptomaster.service.report;

import com.cryptomaster.model.ScalpSignal;
import com.cryptomaster.model.TradingSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
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
        sb.append(String.format("║  Güven:     %% %d (%s yönünde)%n",
                signal.getDirectionalConfidence(), signal.getDirection()));
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

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Hızlı scalp taramasının sonucunu kompakt biçimde yazar. */
    public void printScalpScan(List<ScalpSignal> signals, int scanned) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  ⚡ SCALP TARAMASI (5-10 dk) — %3d sembol tarandı, %2d fırsat   ║%n",
                scanned, signals.size()));
        sb.append("╚══════════════════════════════════════════════════════════════╝");
        log.info(sb.toString());

        if (signals.isEmpty()) {
            log.info("   ⏳ Şu an A+ scalp kurulumu yok. Tahta sakin → girmemek de bir karardır. Sonraki tarama bekleniyor...\n");
            return;
        }

        // En güçlü ilk 10 fırsatı detaylı kart olarak göster
        int shown = 0;
        for (ScalpSignal s : signals) {
            if (shown++ >= 10) break;
            String color = "LONG".equals(s.getDirection()) ? "\033[1;32m" : "\033[1;31m";
            String reset = "\033[0m";
            String icon = "LONG".equals(s.getDirection()) ? "🟢🔼" : "🔴🔽";

            StringBuilder c = new StringBuilder();
            c.append("\n┌──────────────────────────────────────────────────────────────┐\n");
            c.append(String.format(Locale.US, "│ %s %s%-7s%s  %-10s  güven %%%-3d  ⏱ %s%n",
                    icon, color, s.getDirection(), reset, s.getSymbol(), s.getConfidence(),
                    s.getTimestamp().format(HM)));
            c.append(String.format(Locale.US, "│ Giriş %.6f │ SL %.6f │ TP1 %.6f │ TP2 %.6f%n",
                    s.getEntry(), s.getStopLoss(), s.getTp1(), s.getTp2()));
            c.append(String.format(Locale.US, "│ R:R≈1:%.1f │ kaldıraç %dx │ oynaklık %%%.2f │ %s%n",
                    s.getRrRatio(), s.getLeverage(), s.getAtrPct() * 100, s.getHoldEstimate()));
            c.append("│ Gerekçeler:\n");
            if (s.getReasons() != null) {
                for (String r : s.getReasons()) {
                    c.append("│   ").append(r).append("\n");
                }
            }
            c.append("└──────────────────────────────────────────────────────────────┘");
            log.info(c.toString());
        }

        if (signals.size() > shown) {
            log.info("   … ve {} fırsat daha (güven < ilk 10).", signals.size() - shown);
        }
        log.info("⚠️ NOT: Scalp yüksek risklidir. Pozisyon başına bakiyenin %1-2'sinden fazlasını riske atma, SL'siz girme.\n");
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
            String sinyal = switch (s.getDirection()) {
                case "LONG" -> "AL";
                case "SHORT" -> "SAT";
                default -> "BEKLE";
            };
            sb.append(String.format(Locale.US, "│ %-8s │ %s %-6s │ %10.4f │  %% %-3d │  %-4dx  │ %-6s │%n",
                    s.getSymbol(), icon, s.getDirection(), s.getCurrentPrice(),
                    s.getDirectionalConfidence(), s.getLeverage(), sinyal));
        }
        sb.append("└──────────┴──────────┴────────────┴────────┴────────┴────────┘\n");
        log.info(sb.toString());
    }
}