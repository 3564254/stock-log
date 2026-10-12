package com.stocklog.modules.stock.analysis.result;

import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.analysis.score.MomentumScore;
import com.stocklog.modules.stock.analysis.score.TechnicalScore;
import com.stocklog.modules.stock.analysis.score.ValuationScore;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockAnalysisResult {
    private final String         code;
    private final int            score;     // 0~100
    private final String         grade;     // S/A/B/C/D
    private final boolean        isEtf;
    private final ValuationScore valuation;
    private final MomentumScore momentum;
    private final TechnicalScore technical;
    private final TradingSignalResult tradingSignalResult;
}