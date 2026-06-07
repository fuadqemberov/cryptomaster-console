package com.cryptomaster.service.scalp;

import com.cryptomaster.client.BinanceRestClient;
import com.cryptomaster.config.AppConfig;
import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.Kline;
import com.cryptomaster.model.ScalpSignal;
import com.cryptomaster.service.analysis.TechnicalAnalysisService;
import com.cryptomaster.service.report.ConsoleReportService;
import com.cryptomaster.service.strategy.ScalpSignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ana swing analiz döngüsünden BAĞIMSIZ çalışan hızlı scalp tarayıcı.
 *
 * Kendi tek thread'li zamanlayıcısında, kısa periyotla (varsayılan ~45 sn)
 * en likit ilk N sembol için 1m/5m/15m mumları REST'ten çeker, scalp
 * sinyali üretir ve sadece eyleme dönük (LONG/SHORT) olanları raporlar.
 *
 * Likit semboller seçilir çünkü scalp'te slipaj/spread kâr-zarar dengesini bozar.
 */
@Component
public class ScalpScanRunner {
    private static final Logger log = LoggerFactory.getLogger(ScalpScanRunner.class);

    private final BinanceRestClient restClient;
    private final TechnicalAnalysisService analysisService;
    private final ScalpSignalService scalpService;
    private final ConsoleReportService reportService;
    private final AppConfig appConfig;

    private ScheduledExecutorService scheduler;
    private List<String> scalpSymbols = List.of();

    public ScalpScanRunner(BinanceRestClient restClient,
                           TechnicalAnalysisService analysisService,
                           ScalpSignalService scalpService,
                           ConsoleReportService reportService,
                           AppConfig appConfig) {
        this.restClient = restClient;
        this.analysisService = analysisService;
        this.scalpService = scalpService;
        this.reportService = reportService;
        this.appConfig = appConfig;
    }

    /**
     * Scalp tarayıcısını başlatır. {@code symbols} hacme göre azalan sıralı
     * gelmelidir (en likit başta) — ilk N tanesi scalp evreni olur.
     */
    public void start(List<String> symbols) {
        if (!appConfig.isScalpEnabled()) {
            log.info("⏸️ Scalp modu kapalı (cryptomaster.scalp-enabled=false).");
            return;
        }
        int n = Math.min(appConfig.getScalpTopCount(), symbols.size());
        this.scalpSymbols = new ArrayList<>(symbols.subList(0, n));

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scalp-scanner");
            t.setDaemon(true);
            return t;
        });

        int period = appConfig.getScalpIntervalSeconds();
        log.info("⚡ SCALP MODU AKTİF — {} likit sembol, her {} sn taranacak ({}).",
                scalpSymbols.size(), period, String.join("/", appConfig.getScalpIntervals()));
        scheduler.scheduleWithFixedDelay(this::scanSafely, 5, period, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void scanSafely() {
        try {
            scan();
        } catch (Exception e) {
            log.error("❌ Scalp tarama hatası: {}", e.getMessage());
        }
    }

    private void scan() {
        List<String> intervals = appConfig.getScalpIntervals();
        int limit = appConfig.getScalpOhlcLimit();
        List<ScalpSignal> hits = new CopyOnWriteArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);

        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (String symbol : scalpSymbols) {
                futures.add(ex.submit(() -> {
                    try {
                        Map<String, List<Kline>> data = new LinkedHashMap<>();
                        for (String interval : intervals) {
                            List<Kline> k = restClient.getKlines(symbol, interval, limit);
                            if (k.size() >= 30) data.put(interval, k);
                        }
                        if (data.size() < 2) return; // en az 2 zaman dilimi gerekli

                        double price = lastClose(data);
                        if (price <= 0) return;

                        AnalysisResult ar = analysisService.analyze(symbol, data);
                        ScalpSignal signal = scalpService.generate(symbol, price, ar);
                        if (signal.isActionable()) hits.add(signal);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        }

        // Kendi yönündeki güvene göre azalan sırala (en güçlü fırsat üstte)
        hits.sort((a, b) -> Integer.compare(b.getConfidence(), a.getConfidence()));
        reportService.printScalpScan(hits, scalpSymbols.size());
    }

    private double lastClose(Map<String, List<Kline>> data) {
        for (String pref : List.of("1m", "5m", "15m")) {
            List<Kline> k = data.get(pref);
            if (k != null && !k.isEmpty()) return k.get(k.size() - 1).getClose();
        }
        return 0;
    }
}
