package com.stocklog.modules.market.analysis.service.result;

import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;

import java.util.Map;

public interface MarketAnalyzer {
    MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data);
}
