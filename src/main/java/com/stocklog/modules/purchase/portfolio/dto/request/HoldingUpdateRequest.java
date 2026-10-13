package com.stocklog.modules.purchase.portfolio.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record HoldingUpdateRequest(
        String code,
        BigDecimal averagePrice,

        // 선택 입력: null이면 수량 없음으로 저장한다 (수정 화면에서 지운 경우 포함)
        @Positive(message = "보유 수량은 0보다 커야 합니다.")
        BigDecimal quantity
) {
    public static HoldingUpdateRequest stockToHoldings(AddPortfolioStockRequest request) {
        return new HoldingUpdateRequest(
                request.code(),
                request.averagePrice(),
                request.quantity()
        );
    }
}