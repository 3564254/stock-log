package com.stocklog.modules.market.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrendDirection {
    IMPROVING("개선 중", "상승 추세"),
    DETERIORATING("악화 중", "하락 추세"),
    STABLE("안정/횡보", "추세 없음");

    private final String title;
    private final String description;
}