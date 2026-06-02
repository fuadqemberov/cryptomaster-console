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
import java.util.stream.Collectors;

@SpringBootApplication
public class CryptoMasterConsoleApp implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(CryptoMasterConsoleApp.class);

    private final CoinGeckoClient coinGeckoClient;
    private final TechnicalAnalysisService analysisService;
    private final SignalGenerationService signalService;
    private final ConsoleReportService reportService;
    private final AppConfig appConfig;

    // Stablecoin ve özel token'lar (analiz edilmeyecek)
    private static final Set<String> EXCLUDED_IDS = Set.of(
            "tether", "usd-coin", "binance-usd", "dai", "true-usd",
            "leo-token", "staked-ether", "wrapped-bitcoin", "weth",
            "ethena-usde", "first-digital-usd", "usual-usd"
    );

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
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║   🚀 CRYPTOMASTER CONSOLE BOT BAŞLADI        ║");
        log.info("║   📊 {} Coin | Batch: {} | Bekleme: {}sn    ║",
                appConfig.getTopCoinCount(), appConfig.getBatchSize(), appConfig.getBatchWaitSeconds());
        log.info("╚══════════════════════════════════════════════╝\n");

        while (true) {
            // ============ ADIM 1: Coin Listesini Çek ============
            List<CoinMarket> allCoins = fetchCoinList();
            if (allCoins.isEmpty()) {
                log.warn("❌ Coin listesi alınamadı. 60 saniye bekleniyor...");
                Thread.sleep(60_000);
                continue;
            }

            // Stablecoin'leri filtrele
            List<CoinMarket> filteredCoins = allCoins.stream()
                    .filter(coin -> !EXCLUDED_IDS.contains(coin.getId()))
                    .limit(appConfig.getTopCoinCount())
                    .collect(Collectors.toList());

            log.info("✅ {} coin analiz edilecek (stablecoin'ler filtrelendi)", filteredCoins.size());

            // Sembol -> ID eşlemesi
            Map<String, String> symbolToId = new LinkedHashMap<>();
            for (CoinMarket coin : filteredCoins) {
                String symbol = coin.getSymbol().toUpperCase() + "USDT";
                symbolToId.put(symbol, coin.getId());
            }

            // ============ ADIM 2: Batch'ler Halinde Analiz ============
            List<TradingSignal> allSignals = new ArrayList<>();
            List<Map.Entry<String, String>> entries = new ArrayList<>(symbolToId.entrySet());
            int batchSize = appConfig.getBatchSize();
            int totalBatches = (int) Math.ceil((double) entries.size() / batchSize);

            for (int batch = 0; batch < totalBatches; batch++) {
                int start = batch * batchSize;
                int end = Math.min(start + batchSize, entries.size());
                List<Map.Entry<String, String>> batchEntries = entries.subList(start, end);

                log.info("\n📦 Batch {}/{} başlıyor ({} coin)", batch + 1, totalBatches, batchEntries.size());
                log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                for (int i = 0; i < batchEntries.size(); i++) {
                    Map.Entry<String, String> entry = batchEntries.get(i);
                    String symbol = entry.getKey();
                    String coinId = entry.getValue();
                    int globalIndex = start + i + 1;

                    try {
                        // OHLC verilerini çek
                        List<Kline> ohlc1h = coinGeckoClient.getOHLC(coinId, 1);
                        if (ohlc1h == null || ohlc1h.isEmpty()) {
                            log.warn("⚠️ {}/{} {} OHLC(1gün) verisi yok, atlanıyor", globalIndex, entries.size(), symbol);
                            continue;
                        }

                        List<Kline> ohlc4h = coinGeckoClient.getOHLC(coinId, 7);
                        if (ohlc4h == null || ohlc4h.isEmpty()) {
                            log.warn("⚠️ {}/{} {} OHLC(7gün) verisi yok, atlanıyor", globalIndex, entries.size(), symbol);
                            continue;
                        }

                        List<Kline> ohlc1d = coinGeckoClient.getOHLC(coinId, 30);
                        if (ohlc1d == null || ohlc1d.isEmpty()) {
                            log.warn("⚠️ {}/{} {} OHLC(30gün) verisi yok, atlanıyor", globalIndex, entries.size(), symbol);
                            continue;
                        }

                        Map<String, List<Kline>> klinesByInterval = new LinkedHashMap<>();
                        klinesByInterval.put("1h", ohlc1h);
                        klinesByInterval.put("4h", ohlc4h);
                        klinesByInterval.put("1d", ohlc1d);

                        // Güncel fiyat
                        double currentPrice = filteredCoins.stream()
                                .filter(c -> coinId.equals(c.getId()))
                                .findFirst()
                                .map(CoinMarket::getCurrentPrice)
                                .orElse(0.0);

                        // Analiz ve sinyal
                        AnalysisResult analysis = analysisService.analyze(symbol, klinesByInterval);
                        TradingSignal signal = signalService.generateSignal(symbol, currentPrice, analysis);

                        reportService.printDetailedReport(signal);
                        allSignals.add(signal);

                        log.info("✅ {}/{} {} analiz edildi (Güven: %{})",
                                globalIndex, entries.size(), symbol, signal.getConfidence());

                    } catch (Exception e) {
                        log.error("❌ {}/{} {} HATA: {}", globalIndex, entries.size(), symbol, e.getMessage());
                    }
                }

                // Batch arası bekleme (rate limit için)
                if (batch < totalBatches - 1) {
                    int waitSec = appConfig.getBatchWaitSeconds();
                    log.info("\n⏳ Rate limit koruması: {} saniye bekleniyor...", waitSec);
                    Thread.sleep(waitSec * 1000L);
                }
            }

            // ============ ADIM 3: Özet Tablo ============
            reportService.printAllCoinsReport(allSignals);

            // ============ ADIM 4: Ana Döngü Bekleme ============
            log.info("\n🔄 Tüm coin'ler analiz edildi. 5 dakika sonra tekrarlanacak...");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            Thread.sleep(5 * 60 * 1000);
        }
    }

    private List<CoinMarket> fetchCoinList() {
        try {
            log.info("📡 CoinGecko'dan ilk {} coin çekiliyor...", appConfig.getTopCoinCount());
            return coinGeckoClient.getTopCoins(250); // 250 çek, filtrele sonra 100'e düşür
        } catch (Exception e) {
            log.error("❌ Coin listesi alınamadı: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}