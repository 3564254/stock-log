package com.stocklog.domain.portfolio.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.stocklog.global.util.FinancialConst;

@Builder
public record PortfolioResponse(
        List<HoldingResponse> myStocks,
        BigDecimal totalAssets,
        BigDecimal totalProfit,
        BigDecimal totalReturnRate
) {
    // 정적 팩토리 메서드 도입 (of)
    public static PortfolioResponse of(List<HoldingResponse> holdings) {
        return PortfolioResponse.builder()
                .myStocks(holdings)
                .totalAssets(calculateTotalAssets(holdings))
                .totalProfit(calculateTotalProfit(holdings))
                .totalReturnRate(calculateTotalReturnRate(holdings))
                .build();
    }

    // 화면(Thymeleaf) 단의 NPE 방지를 위한 기본값 세팅
    public static PortfolioResponse createFallback() {
        return PortfolioResponse.builder()
                .myStocks(List.of())
                .totalAssets(BigDecimal.ZERO)
                .totalProfit(BigDecimal.ZERO)
                .totalReturnRate(BigDecimal.ZERO)
                .build();
    }

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
        } else {
            return BigDecimal.ZERO;
        }
    }
}