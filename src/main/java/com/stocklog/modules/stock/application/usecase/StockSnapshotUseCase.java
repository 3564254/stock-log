package com.stocklog.modules.stock.application.usecase;

import com.stocklog.modules.stock.application.response.StockSnapshot;

public interface StockSnapshotUseCase {
    StockSnapshot getStockSnapshot(String code);
}
