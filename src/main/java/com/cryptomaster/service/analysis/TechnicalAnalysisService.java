package com.cryptomaster.service.analysis;

import com.cryptomaster.model.AnalysisResult;
import com.cryptomaster.model.Kline;

import java.util.List;
import java.util.Map;

public interface TechnicalAnalysisService {
    AnalysisResult analyze(String symbol, Map<String, List<Kline>> klinesByInterval);
}