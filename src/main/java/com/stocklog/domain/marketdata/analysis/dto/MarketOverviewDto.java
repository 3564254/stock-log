package com.stocklog.domain.marketdata.analysis.dto;

import com.stocklog.domain.marketdata.index.dto.IndexDashboardDto;
import com.stocklog.domain.marketdata.universe.dto.UniverseDto;

import java.util.List;

public record MarketOverviewDto(
        // 1. 핵심 매크로 지표 (IndexType 기반)
        IndexDashboardDto indexDashboard,

        // 2. 섹터별 성과 및 수급 (Universe 기반)
        List<UniverseDto> universeDashboard,

        // 3. 데이터를 융합한 최종 진단
        MarketDiagnosis diagnosis
) {
    public record MarketDiagnosis(
            String marketPhase,        // 예: "RISK_ON", "STAGFLATION_ALARM"
            String summary,            // "금리 인상 우려에도 반도체 섹터가 시장을 방어 중입니다."
            int fearAndGreedScore      // VIX와 수익률을 계산한 자체 점수
    ) {}

}

