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
        MarketAnalyzerScore divergence
) {}