package com.stocklog.domain.stock.provider;

import com.stocklog.domain.stock.dto.request.ChartRequest;
import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.response.StockChartResponse;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Period;

import java.util.List;

public interface StockChartProvider {
    StockChartResponse fetchInfo(ChartRequest request, Period period);
    boolean supports(MarketType marketType);
}
