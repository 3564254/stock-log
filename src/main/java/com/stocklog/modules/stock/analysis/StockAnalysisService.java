package com.stocklog.modules.stock.analysis;


import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.modules.stock.analysis.domain.signal.vo.StockPhase;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MiniSignalDto;
import com.stocklog.modules.stock.analysis.service.*;
import com.stocklog.modules.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockFacadeAnalyzer facadeAnalyzer;

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        return facadeAnalyzer.getAnalysisResult(snapshot);
    }

    public MiniSignalDto getMiniSignal(List<StockChartDto> dailyCharts) {
        return facadeAnalyzer.entryMiniSignalAnalyze(dailyCharts);
    }

    public EntrySignal entrySignalAnalyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        return facadeAnalyzer.entrySignalAnalyze(dailyCharts, weeklyCharts);
    }

    public StockPhase getMarketPhase(MarketContext marketContext) {
        return facadeAnalyzer.getMarketPhase(marketContext);
    }





}