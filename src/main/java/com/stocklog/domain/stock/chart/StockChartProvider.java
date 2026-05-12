package com.stocklog.domain.stock.chart;

import com.stocklog.domain.stock.chart.dto.request.ChartRequest;
import com.stocklog.domain.stock.chart.dto.response.StockChartResponse;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.common.type.Period;


public interface StockChartProvider {
    StockChartResponse fetchInfo(ChartRequest request, Period period);
    boolean supports(MarketType marketType);
}
