package com.cryptomaster.dto.coingecko;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/**
 * CoinGecko /coins/{id}/ohlc dönüşü: [timestamp, open, high, low, close] listeleri
 * Direkt List<List<Object>> olarak alabiliriz, bu sınıf opsiyonel.
 */
public class CoinOHLC {
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<List<Object>> data;

    public List<List<Object>> getData() { return data; }
    public void setData(List<List<Object>> data) { this.data = data; }
}