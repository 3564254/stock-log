package com.stocklog.domain.portfolio.dto.request;

import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record HoldingUpdateRequest(
        String code,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal purchaseExchangeRate
) {
    public static HoldingUpdateRequest stockToHoldings(AddPortfolioStockRequest request) {
        return new HoldingUpdateRequest(
                request.code(),
                request.quantity(),
                request.purchasePrice(),
                request.purchaseExchangeRate()
        );
    }
}