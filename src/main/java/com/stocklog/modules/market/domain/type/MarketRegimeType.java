package com.stocklog.modules.market.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketRegimeType {
    BULL("강세 국면"),
    BEAR("약세 국면"),
    NEUTRAL("방향성 탐색(중립)"),
    TRANSITION("추세 전환기");

    private final String description;


}