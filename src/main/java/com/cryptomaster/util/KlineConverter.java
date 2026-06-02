package com.cryptomaster.util;

import com.cryptomaster.model.Kline;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;

import java.time.Duration;
import java.util.List;

public class KlineConverter {

    /**
     * Kline listesini TA4J BarSeries'e dönüştürür.
     */
    public static BarSeries convertToBarSeries(List<Kline> klines) {
        BaseBarSeries series = new BaseBarSeries();
        for (Kline kline : klines) {
            series.addBar(new BaseBar(
                    Duration.ofHours(1), // CoinGecko OHLC günlük ise 1 günlük kabul edilebilir, burada şimdilik 1 saat
                    kline.getInstant(),
                    kline.getOpen(),
                    kline.getHigh(),
                    kline.getLow(),
                    kline.getClose(),
                    kline.getVolume()
            ));
        }
        return series;
    }
}