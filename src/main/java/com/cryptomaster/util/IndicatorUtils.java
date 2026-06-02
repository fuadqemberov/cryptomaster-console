package com.cryptomaster.util;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public class IndicatorUtils {

    public static double getLastValue(Indicator<Num> indicator) {
        if (indicator.getBarSeries().getEndIndex() < 0) return 0.0;
        return indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue();
    }
}