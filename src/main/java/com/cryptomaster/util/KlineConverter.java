package com.cryptomaster.util;

import com.cryptomaster.model.Kline;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class KlineConverter {

    public static BarSeries convertToBarSeries(List<Kline> klines) {
        BaseBarSeries series = new BaseBarSeries();
        for (Kline kline : klines) {
            ZonedDateTime dateTime = kline.getInstant().atZone(ZoneId.systemDefault());
            series.addBar(BaseBar.builder()
                    .timePeriod(Duration.ofHours(1))   // sembolik
                    .endTime(dateTime)
                    .openPrice(DecimalNum.valueOf(kline.getOpen()))
                    .highPrice(DecimalNum.valueOf(kline.getHigh()))
                    .lowPrice(DecimalNum.valueOf(kline.getLow()))
                    .closePrice(DecimalNum.valueOf(kline.getClose()))
                    .volume(DecimalNum.valueOf(kline.getVolume()))
                    .build()
            );
        }
        return series;
    }
}