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


    // ── 1. Market / Price 그룹 ──
    private final MarketTrendAnalyzer trendAnalyzer;
    private final MarketMomentumAnalyzer momentumAnalyzer;
    private final MarketBreadthAnalyzer breadthAnalyzer;
    private final RiskAnalyzer riskAnalyzer;

    // ── 2. Macro / Liquidity 그룹 ──
    private final RateAnalyzer rateAnalyzer;
    private final LiquidityAnalyzer liquidityAnalyzer;
    private final MacroAnalyzer macroAnalyzer;
    private final CreditAnalyzer creditAnalyzer;

    // ── 3. Cross Asset 그룹 ──
    private final CrossAssetAnalyzer crossAssetAnalyzer;
    private final DivergenceDetector divergenceDetector;

    // ── 4. Sector / Defensive 그룹 ──
    private final SectorRotationAnalyzer sectorAnalyzer;
    private final DefensiveAnalyzer defensiveAnalyzer;

    // ── 5. 최종 종합 엔진 ──
    private final MarketRegimeEngine regimeEngine;

    private final MarketDiagnosisService diagnosisService;



    /**
     * 단일 진입점: 전체 시장 데이터를 1회 조회 후 모든 분석기를 거쳐 최종 국면을 반환
     */
    public MarketRegimeDto analyzeMarket() {
        MarketRegimeDto finalResult = getTotalResult().marketRegimeDto();
        log.info("시장 국면 분석 완료: {}", finalResult.regimeDescription());
        return finalResult;
    }

    public TotalResult getTotalResult() {
        // 1. Data Snapshot 획득 (DB 또는 외부 API 연동하여 필요한 모든 시계열 데이터를 한 번에 적재)
        Map<IndexType, MarketChartData> snapshot = dataProvider.fetchGlobalMarketSnapshot();

        // 2. 그룹별 분석기 실행 (이미 메모리에 올라온 snapshot 데이터만 활용하므로 매우 빠른 속도 보장)

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

        // 3. 분석 결과 종합 (Engine Input 생성)
        EngineInput engineInput = new EngineInput(
                trendScore, momentumScore, breadthScore, riskScore,
                ratesScore, liquidityScore, macroScore, creditScore,
                sectorScore, defensiveScore, crossAssetScore, divergenceScore
        );

        // 4. 엔진을 통한 최종 Market Regime 도출 및 반환
        MarketRegimeDto finalResult = regimeEngine.evaluate(engineInput);
        log.info("[MARKET]: {}", finalResult.regimeDescription());
        return new TotalResult(engineInput, finalResult);
    }

}