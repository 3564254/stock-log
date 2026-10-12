package com.stocklog.modules.stock.analysis;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockFacadeAnalyzer facadeAnalyzer;

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        return facadeAnalyzer.getAnalysisResult(snapshot);
    }

    public TradingSignalResult getTradingSignal(String symbol, MarketContext marketContext) {
        return facadeAnalyzer.analyzeTradingSignal(symbol, marketContext);
    }

    public ChartPhase getMarketPhase(MarketContext marketContext) {
        return facadeAnalyzer.getMarketPhase(marketContext);
    }
}