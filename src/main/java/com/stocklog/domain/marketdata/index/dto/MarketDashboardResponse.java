package com.stocklog.domain.marketdata.index.dto;


import com.stocklog.domain.marketdata.analysis.MarketTrendResult;
import lombok.Builder;

@Builder
public record MarketDashboardResponse(
        IndexResponse sp500,
        IndexResponse nasdaq,
        IndexResponse vix,
        IndexResponse dxy,
        IndexResponse usdKrw,
        IndexResponse us10y,
        IndexResponse us2y,
        IndexResponse wti,
        IndexResponse phlxSemi,
        MarketTrendResult analysis
) {
}
