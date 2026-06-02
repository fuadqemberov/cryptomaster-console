package com.cryptomaster.util;

import com.cryptomaster.model.Kline;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class KlineConverter {

    public static BarSeries convertToBarSeries(List<Kline> klines) {
        BaseBarSeries series = new BaseBarSeries();
        for (Kline kline : klines) {
            ZonedDateTime dateTime = kline.getInstant().atZone(ZoneId.systemDefault());
            series.addBar(new BaseBar(
                    Duration.ofHours(1),  // zaman aralığı (örnek)
                    dateTime,             // ZonedDateTime
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