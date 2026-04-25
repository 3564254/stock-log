package com.stocklog.domain.stock.analysis.result;

import com.stocklog.domain.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.domain.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignal;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StockAnalysisResult {
    private final String         code;
    private final int            score;     // 0~100
    private final String         grade;     // S/A/B/C/D
    private final boolean        isEtf;
    private final List<String>   signals;   // 전체 시그널 통합
    private final ValuationScore valuation;
    private final MomentumScore momentum;
    private final TechnicalScore technical;
    private final EntrySignal entrySignal;

    private StockFinancialsResponse financials;

}