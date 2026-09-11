package com.stocklog.modules.purchase.portfolio.dto.request;

import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record HoldingUpdateRequest(
        String code,
        BigDecimal averagePrice
) {
    public static HoldingUpdateRequest stockToHoldings(AddPortfolioStockRequest request) {
        return new HoldingUpdateRequest(
                request.code(),
                request.averagePrice()
        );
    }
}