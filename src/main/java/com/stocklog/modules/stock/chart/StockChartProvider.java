package com.stocklog.modules.stock.chart;

import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.common.type.Period;


public interface StockChartProvider {
    StockChartResponse fetchChart(ChartRequest request, Period period);
    boolean supports(MarketType marketType);
}
