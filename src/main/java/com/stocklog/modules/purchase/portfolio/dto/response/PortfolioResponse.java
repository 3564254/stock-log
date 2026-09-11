package com.stocklog.modules.purchase.portfolio.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
@Builder
public record PortfolioResponse(
        List<HoldingResponse> myStocks,
        BigDecimal averageReturnRate // 총 자산(totalAssets) 제거, 단순 평균 수익률로 대체
) {
    public static PortfolioResponse of(List<HoldingResponse> holdings) {
        BigDecimal avgRate = holdings.isEmpty() ? BigDecimal.ZERO :
                holdings.stream()
                        .map(HoldingResponse::returnRate)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(holdings.size()), 4, RoundingMode.HALF_UP);

        return PortfolioResponse.builder()
                .myStocks(holdings)
                .averageReturnRate(avgRate)
                .build();
    }

    public static PortfolioResponse createFallback() {
        return PortfolioResponse.builder()
                .myStocks(List.of())
                .averageReturnRate(BigDecimal.ZERO)
                .build();
    }
}