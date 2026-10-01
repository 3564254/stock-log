package com.stocklog.modules.market.analysis.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MarketRegimeDto(
        String regimeDescription,  // 2D 기반 해석 (예: 강한 상승장)
        String regime,             // Axis 1: BULL / BEAR
        String risk,               // Axis 2: RISK-ON / RISK-OFF
        String trendLevel,         // STRONG_BULL 등
        String macroRegime,        // SLOWDOWN 등
        String liquidityRegime,    // EXPANDING 등
        String creditRegime,       // HEALTHY 등
        List<String> leadership,   // 주도 섹터
        List<String> warnings,     // 경고 목록
        List<String> divergences,  // 다이버전스 목록
        int confidence             // 0 ~ 100 신뢰도 스코어
) {
}
