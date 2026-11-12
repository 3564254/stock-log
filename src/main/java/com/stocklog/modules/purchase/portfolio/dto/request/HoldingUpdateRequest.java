package com.stocklog.modules.purchase.portfolio.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record HoldingUpdateRequest(
        String code,
        BigDecimal averagePrice,

        // 선택 입력: null이면 수량 없음으로 저장한다
        @Positive(message = "보유 수량은 0보다 커야 합니다.")
        BigDecimal quantity,

        // 선택: 추가 매수 때 유지할 기존 손절 기준선
        BigDecimal keepStop,

        // 선택: 수량을 줄였을 때 판 가격. 있으면 줄어든 수량을 판 걸로 보고 실현 손익을 매매 기록에 자동으로 남긴다
        @Positive(message = "판 가격은 0보다 커야 합니다.")
        BigDecimal soldPrice,

        // 선택: 보유 방식. null이면 바꾸지 않음, "MOMENTUM"이면 모멘텀 기준 전략, 그 밖의 값("")이면 기본
        String holdStyle
) {
    public static HoldingUpdateRequest stockToHoldings(AddPortfolioStockRequest request) {
        return new HoldingUpdateRequest(
                request.code(),
                request.averagePrice(),
                request.quantity(),
                null,
                null,
                request.holdStyle()
        );
    }
}
