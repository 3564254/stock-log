package com.stocklog.modules.market.open;

import lombok.Builder;

@Builder
public record MarketOpenResponse(
        MarketOpenInfo krMarketOpen,     // 소문자 시작: JSON 키·Thymeleaf 접근이 일반 규칙대로 된다
        MarketOpenInfo usMarketOpen
) {
}

