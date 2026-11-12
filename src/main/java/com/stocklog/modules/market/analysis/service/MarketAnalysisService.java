package com.stocklog.modules.market.analysis.service;

import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.analysis.dto.TotalResult;
import com.stocklog.modules.market.analysis.service.analyzer.*;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.service.result.MarketRegimeEngine;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisService;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.market.index.provider.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAnalysisService {

    private final MarketDataProvider dataProvider;


    // Market / Price 그룹
    private final MarketTrendAnalyzer trendAnalyzer;
    private final MarketMomentumAnalyzer momentumAnalyzer;
    private final MarketBreadthAnalyzer breadthAnalyzer;
    private final RiskAnalyzer riskAnalyzer;

    // Macro / Liquidity 그룹
    private final RateAnalyzer rateAnalyzer;
    private final LiquidityAnalyzer liquidityAnalyzer;
    private final MacroAnalyzer macroAnalyzer;
    private final CreditAnalyzer creditAnalyzer;

    // Cross Asset 그룹
    private final CrossAssetAnalyzer crossAssetAnalyzer;
    private final DivergenceDetector divergenceDetector;

    // Sector / Defensive 그룹
    private final SectorRotationAnalyzer sectorAnalyzer;
    private final DefensiveAnalyzer defensiveAnalyzer;

    // 단기 충격 (급락·변동성 급등)
    private final ShortTermShockDetector shockDetector;

    // 최종 종합 엔진
    private final MarketRegimeEngine regimeEngine;

    private final MarketDiagnosisService diagnosisService;



    /** 단일 진입점: 전체 시장 데이터를 1회 조회 후 모든 분석기를 거쳐 최종 국면을 반환 */
    public MarketRegimeDto analyzeMarket() {
        MarketRegimeDto finalResult = getTotalResult().marketRegimeDto();
        log.info("시장 국면 분석 완료: {}", finalResult.regimeDescription());
        return finalResult;
    }

    private static java.time.LocalDate latestDate(MarketChartData data) {
        if (data == null || data.chartDataList() == null) return null;
        return data.chartDataList().stream()
                .map(c -> c.date())
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }

    public TotalResult getTotalResult() {
        // Data Snapshot 획득
        Map<IndexType, MarketChartData> snapshot = dataProvider.fetchGlobalMarketSnapshot();

        // 그룹별 분석기 실행

        // Market / Price
        MarketAnalyzerScore trendScore = trendAnalyzer.analyze(snapshot);
        MarketAnalyzerScore momentumScore = momentumAnalyzer.analyze(snapshot);
        MarketAnalyzerScore breadthScore = breadthAnalyzer.analyze(snapshot);
        MarketAnalyzerScore riskScore = riskAnalyzer.analyze(snapshot);

        // Macro / Liquidity
        MarketAnalyzerScore ratesScore = rateAnalyzer.analyze(snapshot);
        MarketAnalyzerScore liquidityScore = liquidityAnalyzer.analyze(snapshot);
        MarketAnalyzerScore macroScore = macroAnalyzer.analyze(snapshot);
        MarketAnalyzerScore creditScore = creditAnalyzer.analyze(snapshot);

        // Cross Asset
        MarketAnalyzerScore crossAssetScore = crossAssetAnalyzer.analyze(snapshot);
        MarketAnalyzerScore divergenceScore = divergenceDetector.analyze(snapshot);

        // Sector / Defensive
        MarketAnalyzerScore sectorScore = sectorAnalyzer.analyze(snapshot);
        MarketAnalyzerScore defensiveScore = defensiveAnalyzer.analyze(snapshot);

        // Short-term shock
        MarketAnalyzerScore shockScore = shockDetector.analyze(snapshot);

        // 분석 결과 종합
        EngineInput engineInput = new EngineInput(
                trendScore, momentumScore, breadthScore, riskScore,
                ratesScore, liquidityScore, macroScore, creditScore,
                sectorScore, defensiveScore, crossAssetScore, divergenceScore,
                shockScore
        );

        // 엔진을 통한 최종 Market Regime 도출 및 반환
        MarketRegimeDto finalResult = regimeEngine.evaluate(engineInput);
        log.info("[MARKET]: {}", finalResult.regimeDescription());
        return new TotalResult(engineInput, finalResult, latestDate(snapshot.get(IndexType.SP500)));
    }

}