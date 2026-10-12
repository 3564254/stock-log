package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalAnalyzer;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.application.usecase.GetStockSignalUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetStockSignalService implements GetStockSignalUseCase {

    private final TradingSignalAnalyzer signalAnalyzer;

    public TradingSignalResult getTradingSignalResult(String code, MarketContext ctx) {
        return signalAnalyzer.analyze(code, ctx);
    }
}
