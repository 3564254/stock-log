package com.stocklog.modules.stock.analysis.service;

import com.stocklog.modules.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.modules.stock.analysis.domain.signal.vo.*;
import com.stocklog.modules.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.modules.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.domain.type.AssetType;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.valutation.dto.StockValuationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockFacadeAnalyzer {
    // TODO(패턴): 여러 분석기를 조합해 StockAnalysisResult를 만드는 getAnalysisResult는 Facade 책임에 잘 맞는다.
    // 반면 단순 위임 메서드가 계속 늘면 내부 분석기 API를 다시 노출하는 두 번째 관문이 된다.
    // Facade에는 '조합된 use-case'만 둘지, 단순 분석은 각 Analyzer를 호출할지 경계를 정해 보자.
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final ValuationAnalyzer valuationAnalyzer;
    private final EntrySignalAnalyzer entrySignalAnalyzer;

    public MomentumScore momentumAnalyze(List<StockChartDto> dailyCharts, StockPriceDto priceDto) {
        return momentumAnalyzer.analyze(dailyCharts, priceDto);
    }

    public TechnicalScore technicalAnalyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        return technicalAnalyzer.analyze(dailyCharts, weeklyCharts);
    }

    public ValuationScore valuationAnalyze(StockValuationDto valuation, StockFinancialDto financial, StockInfoDto info, StockPriceDto price) {
        return valuationAnalyzer.analyze(valuation, financial, info, price);
    }

    public EntrySignal entrySignalAnalyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        return entrySignalAnalyzer.analyze(dailyCharts, weeklyCharts);
    }

    public MiniSignalDto entryMiniSignalAnalyze(List<StockChartDto> dailyCharts) {
        return entrySignalAnalyzer.analyzeMini(dailyCharts);
    }

    public StockPhase getMarketPhase(MarketContext context) {
        return entrySignalAnalyzer.getPhase(context);
    }

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        String code = snapshot.info().code();

        // 1. 각 도메인별 분석 실행 및 로그
        MomentumScore momentumScore = momentumAnalyzer.analyze(snapshot.dailyCharts(), snapshot.price());

        TechnicalScore technicalScore = technicalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());

        ValuationScore valuationScore = valuationAnalyzer.analyze(snapshot.valuation(), snapshot.financial(), snapshot.info(), snapshot.price());

        EntrySignal entrySignal = entrySignalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());

        // 2. 종합 점수 및 데이터 가공
        int totalScore = getScore(momentumScore, technicalScore, valuationScore);
        String grade = toGrade(totalScore);
        boolean isEtf = isEtf(snapshot.info());
        List<String> allSignals = getAllSignals(momentumScore, technicalScore, valuationScore);


        return StockAnalysisResult.builder()
                .code(code)
                .score(totalScore)
                .grade(grade)
                .isEtf(isEtf)
                .signals(allSignals)
                .valuation(valuationScore)
                .momentum(momentumScore)
                .technical(technicalScore)
                .entrySignal(entrySignal)
                .build();
    }

    private List<String> getAllSignals(MomentumScore m, TechnicalScore t, ValuationScore v) {
        List<String> signals = new ArrayList<>();
        signals.addAll(m.getSignals());
        signals.addAll(t.getSignals());
        signals.addAll(v.getSignals());
        return signals;
    }

    private boolean isEtf(StockInfoDto infoDto) {
        return !infoDto.assetType().equals(AssetType.COMMON_STOCK);
    }

    private int getScore(MomentumScore m, TechnicalScore t, ValuationScore v) {
        int mScore = m.getScore();
        int tScore = t.getScore();
        int vScore = v.getScore();

        return mScore + tScore + vScore;
    }

    private String toGrade(int score) {
        String grade;
        if (score >= 80) grade = "S";
        else if (score >= 65) grade = "A";
        else if (score >= 48) grade = "B";
        else if (score >= 30) grade = "C";
        else grade = "D";

        return grade;
    }
}
