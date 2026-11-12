package com.stocklog.modules.stock.analysis;

import com.stocklog.modules.stock.analysis.score.ScoreBreakdown;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.analyzer.MomentumAnalyzer;
import com.stocklog.modules.stock.analysis.analyzer.TechnicalAnalyzer;
import com.stocklog.modules.stock.analysis.analyzer.ValuationAnalyzer;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalAnalyzer;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.analysis.score.MomentumScore;
import com.stocklog.modules.stock.analysis.score.TechnicalScore;
import com.stocklog.modules.stock.analysis.score.ValuationScore;
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
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final ValuationAnalyzer valuationAnalyzer;
    private final TradingSignalAnalyzer tradingSignalAnalyzer;

    /** 타점 엔진. 호출하는 쪽이 만든 MarketContext를 그대로 쓰므로 일봉/주봉 조립은 한 곳에서만 한다 */
    public TradingSignalResult analyzeTradingSignal(String symbol, MarketContext context) {
        return tradingSignalAnalyzer.analyze(symbol, context);
    }

    public ChartPhase getMarketPhase(MarketContext context) {
        return PhaseIndicators.determinePhase(context);
    }

    /** 종목 점수만. 매매 규칙의 점수 조건 검증용 */
    public ScoreBreakdown getScoreBreakdown(StockSnapshot snapshot) {
        return ScoreBreakdown.of(
                valuationAnalyzer.analyze(snapshot.valuation(), snapshot.financial(), snapshot.info(), snapshot.price()),
                momentumAnalyzer.analyze(snapshot.dailyCharts(), snapshot.price()),
                technicalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts()));
    }

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        String code = snapshot.info().code();

        // 각 도메인별 분석
        MomentumScore momentumScore = momentumAnalyzer.analyze(snapshot.dailyCharts(), snapshot.price());
        TechnicalScore technicalScore = technicalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());
        ValuationScore valuationScore = valuationAnalyzer.analyze(snapshot.valuation(), snapshot.financial(), snapshot.info(), snapshot.price());

        // 타점 엔진
        MarketContext context = MarketContext.of(snapshot.dailyCharts(), snapshot.weeklyCharts(),
                ChartDataType.STOCK, ExchangeSession.fromCode(code));
        TradingSignalResult tradingSignal = tradingSignalAnalyzer.analyze(code, context);

        // 종합 점수
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