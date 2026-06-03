package com.cryptomaster;

import com.cryptomaster.client.BinanceRestClient;
import com.cryptomaster.client.BinanceWebSocketClient;
import com.cryptomaster.client.BinanceFuturesClient;
import com.cryptomaster.client.LiquidationWebSocketClient;
import com.cryptomaster.client.FearGreedClient;
import com.cryptomaster.config.AppConfig;
import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.DerivativesData;
import com.cryptomaster.model.MarketContext;
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
    private final BinanceFuturesClient binanceFuturesClient;
    private final LiquidationWebSocketClient liquidationWebSocketClient;
    private final FearGreedClient fearGreedClient;

    private static final Set<String> EXCLUDED_SYMBOLS = new HashSet<>(List.of(
            "USDCUSDT", "BUSDUSDT", "DAIUSDT", "TUSDUSDT", "USDPUSDT",
            "FDUSDUSDT", "USDDUSDT", "USDJUSDT", "USTCUSDT"
    ));
    public CryptoMasterConsoleApp(BinanceRestClient binanceRestClient,
                                  BinanceWebSocketClient binanceWebSocketClient,
                                  TechnicalAnalysisService analysisService,
                                  SignalGenerationService signalService,
                                  ConsoleReportService reportService,
                                  AppConfig appConfig,
                                  BinanceFuturesClient binanceFuturesClient,
                                  LiquidationWebSocketClient liquidationWebSocketClient,
                                  FearGreedClient fearGreedClient) {
        this.binanceRestClient = binanceRestClient;
        this.binanceWebSocketClient = binanceWebSocketClient;
        this.analysisService = analysisService;
        this.signalService = signalService;
        this.reportService = reportService;
        this.appConfig = appConfig;
        this.binanceFuturesClient = binanceFuturesClient;
        this.liquidationWebSocketClient = liquidationWebSocketClient;
        this.fearGreedClient = fearGreedClient;
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

        // Likidasyon stream'ini başlat (tüm piyasa)
        if (appConfig.isDerivativesEnabled()) {
            liquidationWebSocketClient.connect();
        }

        // Türev verisi çekilecek sembol kümesi (en yüksek hacimli ilk N)
        Set<String> derivativeSymbols = new HashSet<>(
                symbols.subList(0, Math.min(appConfig.getDerivativesTopCount(), symbols.size())));

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

            // Geçmiş (REST) veriyi koru, sadece en güncel canlı mumu güncelle/ekle
            for (Map.Entry<String, Map<String, List<Kline>>> entry : liveKlineData.entrySet()) {
                String symbol = entry.getKey();
                Map<String, List<Kline>> liveData = entry.getValue();
                if (!allKlineData.containsKey(symbol)) continue;

                for (String interval : appConfig.getOhlcIntervals()) {
                    List<Kline> liveList = liveData.get(interval);
                    List<Kline> baseList = allKlineData.get(symbol).get(interval);
                    if (liveList == null || liveList.isEmpty() || baseList == null || baseList.isEmpty()) continue;

                    Kline lastLive = liveList.get(liveList.size() - 1);
                    List<Kline> merged = new ArrayList<>(baseList);
                    Kline lastBase = merged.get(merged.size() - 1);

                    if (lastBase.getOpenTime() == lastLive.getOpenTime()) {
                        merged.set(merged.size() - 1, lastLive);       // oluşan mumu güncelle
                    } else if (lastLive.getOpenTime() > lastBase.getOpenTime()) {
                        merged.add(lastLive);                           // yeni kapanmış mum ekle
                        if (merged.size() > appConfig.getOhlcLimit()) merged.remove(0);
                    }
                    allKlineData.get(symbol).put(interval, merged);
                }
            }

            // ============ ADIM 4.5: Global bağlam + türev verileri ============
            MarketContext context = buildMarketContext(allKlineData);
            Map<String, DerivativesData> derivativesMap = new ConcurrentHashMap<>();
            if (appConfig.isDerivativesEnabled()) {
                log.info("💹 Türev verileri çekiliyor ({} sembol)...", derivativeSymbols.size());
                Map<String, Double> fundingRates = binanceFuturesClient.getAllFundingRates();
                try (ExecutorService dExec = Executors.newVirtualThreadPerTaskExecutor()) {
                    List<Future<?>> dFutures = new ArrayList<>();
                    for (String sym : derivativeSymbols) {
                        if (!allKlineData.containsKey(sym)) continue;
                        dFutures.add(dExec.submit(() -> {
                            DerivativesData dd = binanceFuturesClient.getDerivatives(
                                    sym, fundingRates.getOrDefault(sym, 0.0));
                            double[] liq = liquidationWebSocketClient.getLiquidations(sym);
                            dd.setLiqLongUsd(liq[0]);
                            dd.setLiqShortUsd(liq[1]);
                            if (liq[0] + liq[1] > 0) dd.setValid(true);
                            derivativesMap.put(sym, dd);
                        }));
                    }
                    for (Future<?> f : dFutures) {
                        try { f.get(20, TimeUnit.SECONDS); } catch (Exception ignored) {}
                    }
                }
                log.info("✅ {} sembol için türev verisi alındı. (F&G: {} / BTC: {})",
                        derivativesMap.size(), (int) context.getFearGreed(),
                        context.getBtcRegime() > 0 ? "boğa" : context.getBtcRegime() < 0 ? "ayı" : "nötr");
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
                            DerivativesData dd = derivativesMap.get(symbol);
                            TradingSignal signal = signalService.generateSignal(symbol, currentPrice, analysis, dd, context);

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

    /** Fear&Greed + BTC 1d rejimi ile global bağlam üretir. */
    private MarketContext buildMarketContext(Map<String, Map<String, List<Kline>>> allKlineData) {
        MarketContext ctx = new MarketContext();
        try {
            double[] fg = fearGreedClient.fetchValue();
            ctx.setFearGreed(fg[0]);
            if (fg[1] == 1) ctx.setFearGreedLabel(fearGreedClient.fetchLabel());
        } catch (Exception ignored) {}

        try {
            Map<String, List<Kline>> btc = allKlineData.get("BTCUSDT");
            if (btc != null && btc.containsKey("1d")) {
                Map<String, List<Kline>> data = new LinkedHashMap<>();
                data.put("1d", btc.get("1d"));
                AnalysisResult ar = analysisService.analyze("BTCUSDT", data);
                AnalysisResult.TimeframeAnalysis tf = ar.getTimeframeAnalyses().get("1d");
                if (tf != null) {
                    if ("UPTREND".equals(tf.getTrend())) ctx.setBtcRegime(1);
                    else if ("DOWNTREND".equals(tf.getTrend())) ctx.setBtcRegime(-1);
                }
            }
        } catch (Exception ignored) {}
        return ctx;
    }
}