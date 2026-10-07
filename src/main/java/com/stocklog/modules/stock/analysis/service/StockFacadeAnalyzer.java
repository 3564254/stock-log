package com.stocklog.modules.stock.analysis.service;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.diagnosis.TradingSignalAnalyzer;
import com.stocklog.modules.stock.analysis.diagnosis.TradingSignalResult;
import com.stocklog.modules.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.modules.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.modules.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.modules.stock.domain.type.AssetType;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockFacadeAnalyzer {
    // TODO(패턴): 여러 분석기를 조합해 StockAnalysisResult를 만드는 getAnalysisResult는 Facade 책임에 잘 맞는다.
    // 단순 위임 메서드가 늘면 내부 분석기 API를 다시 노출하는 두 번째 관문이 되므로,
    // Facade에는 '조합된 use-case'만 두고 단순 분석은 각 Analyzer를 직접 호출하는 경계를 유지한다.
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final ValuationAnalyzer valuationAnalyzer;
    private final TradingSignalAnalyzer tradingSignalAnalyzer;

    /** 타점 엔진. 호출하는 쪽이 만든 MarketContext를 그대로 쓰므로 일봉/주봉 조립은 한 곳에서만 한다. */
    public TradingSignalResult analyzeTradingSignal(String symbol, MarketContext context) {
        return tradingSignalAnalyzer.analyze(symbol, context);
    }

    public ChartPhase getMarketPhase(MarketContext context) {
        return PhaseIndicators.determinePhase(context);
    }

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        String code = snapshot.info().code();

        // 1. 각 도메인별 분석
        MomentumScore momentumScore = momentumAnalyzer.analyze(snapshot.dailyCharts(), snapshot.price());
        TechnicalScore technicalScore = technicalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());
        ValuationScore valuationScore = valuationAnalyzer.analyze(snapshot.valuation(), snapshot.financial(), snapshot.info(), snapshot.price());

        // 2. 타점 엔진 (차트가 부족하면 MarketContext가 fallback으로 만들어져 INSUFFICIENT_DATA 결과가 나온다)
        MarketContext context = MarketContext.of(snapshot.dailyCharts(), snapshot.weeklyCharts(),
                ChartDataType.STOCK, ExchangeSession.fromCode(code));
        TradingSignalResult tradingSignal = tradingSignalAnalyzer.analyze(code, context);

        // 3. 종합 점수
        int totalScore = getScore(momentumScore, technicalScore, valuationScore);

        return StockAnalysisResult.builder()
                .code(code)
                .score(totalScore)
                .grade(toGrade(totalScore))
                .isEtf(isEtf(snapshot.info()))
                .valuation(valuationScore)
                .momentum(momentumScore)
                .technical(technicalScore)
                .tradingSignalResult(tradingSignal)
                .build();
    }

    private boolean isEtf(StockInfoDto infoDto) {
        return !infoDto.assetType().equals(AssetType.COMMON_STOCK);
    }

    private int getScore(MomentumScore m, TechnicalScore t, ValuationScore v) {
        return m.getScore() + t.getScore() + v.getScore();
    }

    private String toGrade(int score) {
        if (score >= 80) return "S";
        if (score >= 65) return "A";
        if (score >= 48) return "B";
        if (score >= 30) return "C";
        return "D";
    }
}