package com.stocklog.modules.stock.application.usecase;

import com.stocklog.modules.stock.chart.dto.StockChartDto;

import java.util.List;

public interface GetStockChartUseCase {
    List<StockChartDto> getStockChartDtoList(String code);
}
