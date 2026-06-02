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

        // Coin listesini al (ilk 50)
        List<CoinMarket> topCoins = coinGeckoClient.getTopCoins(50);
        List<String> watchlist = new ArrayList<>(appConfig.getCoins()); // BTCUSDT,ETHUSDT,...
        // Eğer config'de yoksa top 50'den ilk 10'u ekleyelim (örnek)
        if (watchlist.isEmpty()) {
            topCoins.stream().limit(10).forEach(c -> watchlist.add(c.getSymbol().toUpperCase() + "USDT"));
        }

        // Sonsuz döngü
        while (true) {
            List<TradingSignal> allSignals = new ArrayList<>();
            for (String symbol : watchlist) {
                try {
                    String coinId = symbol.replace("USDT", "").toLowerCase(); // basit eşleştirme
                    // OHLC verilerini çek
                    Map<String, List<Kline>> klinesByInterval = new LinkedHashMap<>();
                    klinesByInterval.put("1h", coinGeckoClient.getOHLC(coinId, 1));
                    klinesByInterval.put("4h", coinGeckoClient.getOHLC(coinId, 7));
                    klinesByInterval.put("1d", coinGeckoClient.getOHLC(coinId, 30));

                    // Analiz
                    AnalysisResult analysis = analysisService.analyze(symbol, klinesByInterval);

                    // Güncel fiyat
                    double currentPrice = coinGeckoClient.getTopCoins(1).stream()
                            .filter(c -> symbol.equalsIgnoreCase(c.getSymbol() + "USDT"))
                            .findFirst().map(CoinMarket::getCurrentPrice).orElse(0.0);

                    TradingSignal signal = signalService.generateSignal(symbol, currentPrice, analysis);
                    reportService.printDetailedReport(signal);
                    allSignals.add(signal);
                } catch (Exception e) {
                    log.error("{} analiz edilemedi: {}", symbol, e.getMessage());
                }
            }
            reportService.printAllCoinsReport(allSignals);

            log.info("⏳ Sonraki analiz 5 dakika sonra...\n");
            Thread.sleep(5 * 60 * 1000);
        }
    }
}