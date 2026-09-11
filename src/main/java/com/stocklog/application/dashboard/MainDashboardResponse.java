package com.stocklog.application.dashboard;

import com.stocklog.modules.purchase.portfolio.dto.SectorWeightDto;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.trade.dto.vo.WatchItemDto;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

@Builder
public record MainDashboardResponse(
        PortfolioResponse portfolioResponse,
        PortfolioAnalysisResponse portfolioAnalysisResponse,
        List<SectorWeightDto> sectorWeightDtoList,
        List<WatchItemDto> watchListResponse
) {

    public static MainDashboardResponse createFallback() {
        return MainDashboardResponse.builder()
                .portfolioResponse(PortfolioResponse.createFallback())
                .portfolioAnalysisResponse(null)
                .sectorWeightDtoList(Collections.emptyList())
                .watchListResponse(Collections.emptyList())
                .build();
    }
}
