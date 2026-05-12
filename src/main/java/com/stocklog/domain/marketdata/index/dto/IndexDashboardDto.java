package com.stocklog.domain.marketdata.index.dto;


import com.stocklog.domain.marketdata.analysis.MarketTrendResult;
import lombok.Builder;

@Builder
public record IndexDashboardDto(
        IndexDto sp500,
        IndexDto nasdaq,
        IndexDto vix,
        IndexDto dxy,
        IndexDto usdKrw,
        IndexDto us10y,
        IndexDto us2y,
        IndexDto wti,
        IndexDto phlxSemi,

        // ── 신규 추가 추천 필드 ──
        IndexDto totalMarket,     // WILL5000INDFC (미국 전체 시장 흐름)
        IndexDto yieldCurve       // T10Y2Y (장단기 금리차 - 침체 시그널)
) {
}
