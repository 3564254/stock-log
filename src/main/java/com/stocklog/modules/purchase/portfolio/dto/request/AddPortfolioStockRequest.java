package com.stocklog.modules.purchase.portfolio.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record AddPortfolioStockRequest(
        @NotBlank(message = "종목 코드는 필수입니다.")
        String code,

        @NotBlank(message = "종목명은 필수입니다.")
        String name,

        @NotNull(message = "평균 매수단가를 입력해주세요.")
        @DecimalMin(value = "0.0", message = "단가는 0 이상이어야 합니다.")
        BigDecimal averagePrice,

        // 선택 입력: 비워 두면 null
        @Positive(message = "보유 수량은 0보다 커야 합니다.")
        BigDecimal quantity
) {}