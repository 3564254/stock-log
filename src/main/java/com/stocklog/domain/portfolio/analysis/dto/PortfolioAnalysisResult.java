package com.stocklog.domain.portfolio.analysis.dto;

import com.stocklog.domain.portfolio.dto.response.HoldingResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.global.util.FinancialConst;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Builder
public record PortfolioAnalysisResult(
        // 1. 기본 자산 정보
        List<HoldingResponse> myStocks,
        BigDecimal totalAssets,
        BigDecimal totalProfit,
        BigDecimal totalReturnRate,

        // 2. 분석 정보
        PortfolioAnalysisResponse analysis
) {

    public static PortfolioAnalysisResult of(List<HoldingResponse> holdings, PortfolioAnalysisResponse analysisResponse) {
        return PortfolioAnalysisResult.builder()
                .myStocks(holdings)
                .totalAssets(calculateTotalAssets(holdings))
                .totalProfit(calculateTotalProfit(holdings))
                .totalReturnRate(calculateTotalReturnRate(holdings))
                .analysis(analysisResponse)
                .build();
    }

    public static PortfolioAnalysisResult createEmpty() {
        return PortfolioAnalysisResult.builder()
                .myStocks(List.of())
                .totalAssets(BigDecimal.ZERO)
                .totalProfit(BigDecimal.ZERO)
                .totalReturnRate(BigDecimal.ZERO)
                .analysis(null)
                .build();
    }

    // --- 내부 계산 로직 (Private) ---

    private static BigDecimal calculateTotalAssets(List<HoldingResponse> holdings) {
        return holdings.stream()
                .map(HoldingResponse::getCurrentValueKrw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal calculateTotalProfit(List<HoldingResponse> holdings) {
        return holdings.stream()
                .map(HoldingResponse::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal calculateTotalReturnRate(List<HoldingResponse> holdings) {
        BigDecimal totalProfit = calculateTotalProfit(holdings);
        BigDecimal totalPurchaseKrw = holdings.stream()
                .map(HoldingResponse::getPurchaseValueKrw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPurchaseKrw.compareTo(BigDecimal.ZERO) > 0) {
            return totalProfit
                    .divide(totalPurchaseKrw, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }
}