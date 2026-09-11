package com.stocklog.modules.stock.application.usecase;

import com.stocklog.modules.stock.application.response.StockSummary;

public interface StockSummaryUseCase {
    StockSummary getStockSummary(String code);
}
