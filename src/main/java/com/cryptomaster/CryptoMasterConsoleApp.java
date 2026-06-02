package com.cryptomaster;

import com.cryptomaster.client.BinanceRestClient;
import com.cryptomaster.client.BinanceWebSocketClient;
import com.cryptomaster.config.AppConfig;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class CryptoMasterConsoleApp implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(CryptoMasterConsoleApp.class);

    private final BinanceRestClient binanceRestClient;
    private final BinanceWebSocketClient binanceWebSocketClient;
    private final TechnicalAnalysisService analysisService;
    private final SignalGenerationService signalService;
    private final ConsoleReportService reportService;
    private final AppConfig appConfig;

    private static final Set<String> EXCLUDED_SYMBOLS = new HashSet<>(List.of(
            "USDCUSDT", "BUSDUSDT", "DAIUSDT", "TUSDUSDT", "USDPUSDT",
            "FDUSDUSDT", "USDDUSDT", "USDJUSDT", "USTCUSDT"
    ));
    public CryptoMasterConsoleApp(BinanceRestClient binanceRestClient,
                                  BinanceWebSocketClient binanceWebSocketClient,
                                  TechnicalAnalysisService analysisService,
                                  SignalGenerationService signalService,
                                  ConsoleReportService reportService,
                                  AppConfig appConfig) {
        this.binanceRestClient = binanceRestClient;
        this.binanceWebSocketClient = binanceWebSocketClient;
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
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║   🚀 CRYPTOMASTER BINANCE WEBSOCKET BOT          ║");
        log.info("║   📊 {} Coin | ⚡ Gerçek Zamanlı Analiz         ║", appConfig.getTopCoinCount());
        log.info("╚══════════════════════════════════════════════════╝\n");

        // ============ ADIM 1: Tüm USDT çiftlerini al ============
        Map<String, Double> allPairs = binanceRestClient.getAllUSDTPairs();

        // Filtrele ve sembolleri al
        List<String> symbols = allPairs.keySet().stream()
                .filter(sym -> !EXCLUDED_SYMBOLS.contains(sym))
                .limit(appConfig.getTopCoinCount())
                .toList();

        log.info("🎯 {} sembol analiz edilecek.", symbols.size());
        log.info("📡 WebSocket bağlantısı başlatılıyor...\n");

        // ============ ADIM 2: WebSocket bağlantısını başlat ============
        binanceWebSocketClient.connect(symbols);

        // ============ ADIM 3: Geçmiş veriyi REST'ten çek (ilk doldurma) ============
        log.info("📊 Geçmiş OHLC verileri çekiliyor...");
        Map<String, Map<String, List<Kline>>> allKlineData = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completed = new AtomicInteger(0);

            for (String symbol : symbols) {
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, List<Kline>> symbolKlines = new ConcurrentHashMap<>();
                        for (String interval : appConfig.getOhlcIntervals()) {
                            List<Kline> klines = binanceRestClient.getKlines(symbol, interval, appConfig.getOhlcLimit());
                            if (!klines.isEmpty()) {
                                symbolKlines.put(interval, klines);
                            }
                        }
                        if (!symbolKlines.isEmpty()) {
                            allKlineData.put(symbol, symbolKlines);
                        }
                        int done = completed.incrementAndGet();
                        if (done % 50 == 0) {
                            log.info("   ⏳ {}/{} sembol yüklendi...", done, symbols.size());
                        }
                    } catch (Exception e) {
                        log.error("❌ {} veri çekme hatası: {}", symbol, e.getMessage());
                    }
                }));
            }

            // Tüm işlemlerin bitmesini bekle
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.warn("⚠️ Zaman aşımı, devam ediliyor...");
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        log.info("✅ {} sembol için geçmiş veri yüklendi.\n", allKlineData.size());

        // ============ ADIM 4: Ana Analiz Döngüsü ============
        while (true) {
            log.info("╔══════════════════════════════════════════════════╗");
            log.info("║  🔍 ANALİZ BAŞLADI                              ║");
            log.info("╚══════════════════════════════════════════════════╝");

            // WebSocket'ten gelen canlı veriyi al
            Map<String, Map<String, List<Kline>>> liveKlineData = binanceWebSocketClient.getKlineCache();
            Map<String, Double> livePrices = binanceWebSocketClient.getPriceCache();

            // Geçmiş veri ile canlı veriyi birleştir
            for (Map.Entry<String, Map<String, List<Kline>>> entry : liveKlineData.entrySet()) {
                String symbol = entry.getKey();
                Map<String, List<Kline>> liveData = entry.getValue();
                if (allKlineData.containsKey(symbol) && liveData.containsKey("1m")) {
                    allKlineData.get(symbol).put("1m", liveData.get("1m"));
                }
            }

            // ============ ADIM 5: Paralel Analiz (Virtual Thread) ============
            List<TradingSignal> allSignals = new CopyOnWriteArrayList<>();
            AtomicInteger analyzedCount = new AtomicInteger(0);
            int totalSymbols = allKlineData.size();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>();

                for (Map.Entry<String, Map<String, List<Kline>>> entry : allKlineData.entrySet()) {
                    String symbol = entry.getKey();
                    Map<String, List<Kline>> klineMap = entry.getValue();

                    futures.add(executor.submit(() -> {
                        try {
                            double currentPrice = livePrices.getOrDefault(symbol, 0.0);

                            // Sadece 1h, 4h, 1d verilerini kullan
                            Map<String, List<Kline>> analysisData = new LinkedHashMap<>();
                            if (klineMap.containsKey("1h")) analysisData.put("1h", klineMap.get("1h"));
                            if (klineMap.containsKey("4h")) analysisData.put("4h", klineMap.get("4h"));
                            if (klineMap.containsKey("1d")) analysisData.put("1d", klineMap.get("1d"));

                            if (analysisData.isEmpty()) return;

                            AnalysisResult analysis = analysisService.analyze(symbol, analysisData);
                            TradingSignal signal = signalService.generateSignal(symbol, currentPrice, analysis);

                            if (signal.getDirection().equals("LONG") || signal.getDirection().equals("SHORT")) {
                                allSignals.add(signal);
                            }

                            int done = analyzedCount.incrementAndGet();
                            if (done % 50 == 0) {
                                log.info("   ⏳ {}/{} sembol analiz edildi...", done, totalSymbols);
                            }
                        } catch (Exception e) {
                            log.error("❌ {} analiz hatası: {}", symbol, e.getMessage());
                            analyzedCount.incrementAndGet();
                        }
                    }));
                }

                // Bekle
                for (Future<?> future : futures) {
                    try {
                        future.get(10, TimeUnit.SECONDS);
                    } catch (TimeoutException | ExecutionException e) {
                        // Devam et
                    }
                }
            }

            // ============ ADIM 6: Sonuçları Sırala ve Raporla ============
            allSignals.sort((a, b) -> {
                if (a.getDirection().equals(b.getDirection())) {
                    return Integer.compare(b.getConfidence(), a.getConfidence());
                }
                return a.getDirection().compareTo(b.getDirection());
            });

            // İlk 30 sinyali detaylı göster
            log.info("\n📊 EN GÜÇLÜ 30 SİNYAL:");
            log.info("═══════════════════════════════════════════════════");
            allSignals.stream().limit(30).forEach(reportService::printDetailedReport);

            // Tüm sinyallerin özet tablosu
            reportService.printAllCoinsReport(allSignals);

            log.info("\n✅ {} sembol analiz edildi, {} sinyal bulundu.",
                    totalSymbols, allSignals.size());
            log.info("⏳ {} saniye sonra yeni analiz...\n",
                    appConfig.getAnalysisIntervalSeconds());

            Thread.sleep(appConfig.getAnalysisIntervalSeconds() * 1000L);
        }
    }
}