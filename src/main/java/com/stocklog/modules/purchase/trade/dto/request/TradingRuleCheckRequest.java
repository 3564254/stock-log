package com.stocklog.modules.purchase.trade.dto.request;

import jakarta.validation.constraints.NotNull;

public record TradingRuleCheckRequest(
        @NotNull(message = "진입 대기 큐 식별자는 필수 항목임")
        Long wishListId,

        Double currentRsi,   // 실시간 RSI 수치
        String ma20Trend,    // 20일선 추세 방향 (UP, DOWN, FLAT)
        Double currentVolume // 실시간 거래량 수치
) {
}
