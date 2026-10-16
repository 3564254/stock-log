package com.stocklog.modules.market.analysis.dto;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;

// 분석기 전체 결과를 담는 입력 레코드
public record EngineInput(
        MarketAnalyzerScore trend,
        MarketAnalyzerScore momentum,
        MarketAnalyzerScore breadth,
        MarketAnalyzerScore risk,
        MarketAnalyzerScore rates,
        MarketAnalyzerScore liquidity,
        MarketAnalyzerScore macro,
        MarketAnalyzerScore credit,
        MarketAnalyzerScore sector,
        MarketAnalyzerScore defensive,
        MarketAnalyzerScore crossAsset,
        MarketAnalyzerScore divergence,
        MarketAnalyzerScore shock          // 단기 급락·변동성 급등 감지 (가중합에는 넣지 않고 국면 문구·경고에만 쓴다)
) {}