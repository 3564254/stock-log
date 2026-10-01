package com.stocklog.modules.market.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RiskAppetiteType {
    RISK_ON("위험자산 선호", "#FF5252"),  // 주식, 코인 등 선호
    RISK_OFF("안전자산 선호", "#448AFF"), // 국채, 달러 등 선호
    NEUTRAL("중립", "#9E9E9E");

    private final String description;
    private final String uiColorCode;

}