package com.cryptomaster;

import com.cryptomaster.client.CoinGeckoClient;
import com.cryptomaster.config.AppConfig;
import com.cryptomaster.dto.coingecko.CoinMarket;
import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.Kline;
import com.cryptomaster.model.TradingSignal;
import com.cryptomaster.service.analysis.TechnicalAnalysisService;
import com.cryptomaster.service.report.ConsoleReportService;
import com.cryptomaster.service.strategy.SignalGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootApplication
public class CryptoMasterConsoleApp implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(CryptoMasterConsoleApp.class);

    private final CoinGeckoClient coinGeckoClient;
    private final TechnicalAnalysisService analysisService;
    private final SignalGenerationService signalService;
    private final ConsoleReportService reportService;
    private final AppConfig appConfig;

    public CryptoMasterConsoleApp(CoinGeckoClient coinGeckoClient,
                                  TechnicalAnalysisService analysisService,
                                  SignalGenerationService signalService,
                                  ConsoleReportService reportService,
                                  AppConfig appConfig) {
        this.coinGeckoClient = coinGeckoClient;
        this.analysisService = analysisService;
        this.signalService = signalService;
        this.reportService = reportService;
        this.appConfig = appConfig;
    }

    public static void main(String[] args) {
        SpringApplication.run(CryptoMasterConsoleApp.class, args);
    }

    @Override
    public void run(String... args) throws InterruptedException {
        log.info("=== CRYPTOMASTER CONSOLE BOT BAŞLADI (CoinGecko) ===");

        while (true) {
            int topCoinCount = appConfig.getTopCoinCount();
            log.info("📊 CoinGecko'dan ilk {} coin çekiliyor...", topCoinCount);
            List<CoinMarket> topCoins = coinGeckoClient.getTopCoins(topCoinCount);

            if (topCoins == null || topCoins.isEmpty()) {
                log.warn("❌ Coin listesi alınamadı, 1 dakika bekleniyor...");
                Thread.sleep(60_000);
                continue;
            }

            Map<String, String> symbolToId = new LinkedHashMap<>();
            for (CoinMarket coin : topCoins) {
                String symbol = coin.getSymbol().toUpperCase() + "USDT";
                symbolToId.put(symbol, coin.getId());
            }

            List<TradingSignal> allSignals = new ArrayList<>();
            int coinIndex = 0;
            for (Map.Entry<String, String> entry : symbolToId.entrySet()) {
                coinIndex++;
                String symbol = entry.getKey();
                String coinId = entry.getValue();

                try {
                    Map<String, List<Kline>> klinesByInterval = new LinkedHashMap<>();
                    klinesByInterval.put("1h", coinGeckoClient.getOHLC(coinId, 1));
                    klinesByInterval.put("4h", coinGeckoClient.getOHLC(coinId, 7));
                    klinesByInterval.put("1d", coinGeckoClient.getOHLC(coinId, 30));

                    double currentPrice = topCoins.stream()
                            .filter(c -> coinId.equals(c.getId()))
                            .findFirst()
                            .map(CoinMarket::getCurrentPrice)
                            .orElse(0.0);

                    AnalysisResult analysis = analysisService.analyze(symbol, klinesByInterval);
                    TradingSignal signal = signalService.generateSignal(symbol, currentPrice, analysis);

                    reportService.printDetailedReport(signal);
                    allSignals.add(signal);

                    log.info("✅ {}/{} {} analiz edildi.", coinIndex, symbolToId.size(), symbol);
                } catch (Exception e) {
                    log.error("❌ {}/{} {} analiz edilemedi: {}", coinIndex, symbolToId.size(), symbol, e.getMessage());
                }
            }

            reportService.printAllCoinsReport(allSignals);
            log.info("\n⏳ Sonraki analiz 5 dakika sonra... ({})",
                    LocalTime.now().plusMinutes(5).truncatedTo(ChronoUnit.SECONDS));
            Thread.sleep(5 * 60 * 1000);
        }
    }
}