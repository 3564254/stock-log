package com.stocklog.modules.purchase.portfolio.analysis.dto;

import com.stocklog.modules.purchase.portfolio.dto.response.HoldingResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record PortfolioAnalysisResult(
        // 1. 기본 자산 정보
        List<HoldingResponse> myStocks,

        // 2. 분석 정보
        PortfolioAnalysisResponse analysis
) {

    public static PortfolioAnalysisResult of(List<HoldingResponse> holdings, PortfolioAnalysisResponse analysisResponse) {
        return PortfolioAnalysisResult.builder()
                .myStocks(holdings)
                .analysis(analysisResponse)
                .build();
    }

    public static PortfolioAnalysisResult createEmpty() {
        return PortfolioAnalysisResult.builder()
                .myStocks(List.of())
                .analysis(null)
                .build();
    }
}