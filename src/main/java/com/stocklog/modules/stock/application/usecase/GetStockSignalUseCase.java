package com.stocklog.modules.stock.application.usecase;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;

public interface GetStockSignalUseCase {
    TradingSignalResult getTradingSignalResult(String code, MarketContext ctx);
}
