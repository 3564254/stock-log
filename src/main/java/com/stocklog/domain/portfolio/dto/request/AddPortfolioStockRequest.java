package com.stocklog.domain.portfolio.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AddPortfolioStockRequest (
        @NotBlank(message = "종목 코드는 필수입니다.")
        String code,

        @NotBlank(message = "종목명은 필수입니다.")
        String name,

        @NotNull(message = "수량을 입력해주세요.")
        @DecimalMin(value = "0.0", message = "수량은 0 이상이어야 합니다.")
        BigDecimal quantity,

        @NotNull(message = "매수단가를 입력해주세요.")
        @DecimalMin(value = "0.0", message = "매수단가는 0 이상이어야 합니다.")
        BigDecimal purchasePrice,

        // 한국 주식은 1.0
        @NotNull(message = "구매 당시 환율을 입력해주세요.")
        BigDecimal purchaseExchangeRate
) {
    public static AddPortfolioStockRequest createFallback() {
        return AddPortfolioStockRequest.builder()
                .code("")
                .name("")
                .quantity(BigDecimal.ZERO)
                .purchasePrice(BigDecimal.ZERO)
                .purchaseExchangeRate(BigDecimal.ONE) // 곱셈을 위해 기본값 1.0이 안전함
                .build();
    }
}